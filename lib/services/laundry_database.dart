import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:path/path.dart';
import 'package:sqflite/sqflite.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

class AppConfig {
  static const bool isProductionMode = true;
  static const bool useSupabase = false;

  // Set these if you switch to Supabase in production.
  static const String supabaseUrl = 'https://YOUR_PROJECT_URL.supabase.co';
  static const String supabaseAnonKey = 'YOUR_ANON_KEY';
}

abstract class LaundryDatabase {
  Future<void> initialize();

  Future<Map<String, dynamic>?> login({required String username, required String password});

  Future<List<Map<String, dynamic>>> getKycRows();
  Future<List<Map<String, dynamic>>> getPayoutRows();
  Future<List<Map<String, dynamic>>> getOrderRows();
  Future<List<Map<String, dynamic>>> getCustomerRows();
  Future<Map<String, dynamic>> getDashboardSummary();
  Future<void> addPayout({
    required String merchant,
    required double amount,
  });
  Future<void> approvePayout(int id);

  Future<String> exportPayoutCsv();
}

class DatabaseService {
  DatabaseService._();
  static final DatabaseService instance = DatabaseService._();

  final LaundryDatabase _backend = AppConfig.useSupabase
      ? SupabaseLaundryDatabase()
      : SqliteLaundryDatabase();

  Future<void> initialize() => _backend.initialize();

  Future<Map<String, dynamic>?> login({
    required String username,
    required String password,
  }) =>
      _backend.login(username: username, password: password);

  Future<List<Map<String, dynamic>>> getKycRows() => _backend.getKycRows();
  Future<List<Map<String, dynamic>>> getPayoutRows() => _backend.getPayoutRows();
  Future<List<Map<String, dynamic>>> getOrderRows() => _backend.getOrderRows();
  Future<List<Map<String, dynamic>>> getCustomerRows() => _backend.getCustomerRows();
  Future<Map<String, dynamic>> getDashboardSummary() => _backend.getDashboardSummary();

  Future<void> addPayout({
    required String merchant,
    required double amount,
  }) =>
      _backend.addPayout(merchant: merchant, amount: amount);

  Future<void> approvePayout(int id) => _backend.approvePayout(id);

  Future<String> exportPayoutCsv() => _backend.exportPayoutCsv();
}

class SqliteLaundryDatabase implements LaundryDatabase {
  SqliteLaundryDatabase();

  static Database? _database;

  @override
  Future<void> initialize() async {
    if (_database != null) return;

    final dbPath = await getDatabasesPath();
    final path = join(dbPath, 'laundry_marketplace.db');

    _database = await openDatabase(
      path,
      version: 2,
      onCreate: _onCreate,
      onUpgrade: _onUpgrade,
    );

    await _ensureUsers();
  }

  @override
  Future<Map<String, dynamic>?> login({
    required String username,
    required String password,
  }) async {
    await initialize();

    final db = _database!;
    final rows = await db.query(
      'admin_users',
      where: 'username = ?',
      whereArgs: [username.trim()],
      limit: 1,
    );

    if (rows.isEmpty) return null;

    final savedHash = rows.first['passwordHash'] as String;
    final inputHash = sha256.convert(utf8.encode(password)).toString();

    if (savedHash != inputHash) return null;

    return {
      'username': rows.first['username'],
      'role': rows.first['role'],
    };
  }

  @override
  Future<List<Map<String, dynamic>>> getKycRows() async {
    await initialize();
    return _database!.query('kyc', orderBy: 'id DESC');
  }

  @override
  Future<List<Map<String, dynamic>>> getPayoutRows() async {
    await initialize();
    return _database!.query('payouts', orderBy: 'id DESC');
  }

  @override
  Future<List<Map<String, dynamic>>> getOrderRows() async {
    await initialize();
    return _database!.query('orders', orderBy: 'id DESC');
  }

  @override
  Future<List<Map<String, dynamic>>> getCustomerRows() async {
    await initialize();
    return _database!.query('customers', orderBy: 'spend DESC');
  }

  @override
  Future<Map<String, dynamic>> getDashboardSummary() async {
    await initialize();
    final db = _database!;

    final totalRevenue = await db.rawQuery(
      'SELECT COALESCE(SUM(total), 0) as total FROM orders',
    );
    final totalCustomers = await db.rawQuery(
      'SELECT COUNT(*) as total FROM customers',
    );
    final kycCount = await db.rawQuery(
      'SELECT status, COUNT(*) as total FROM kyc GROUP BY status',
    );
    final payouts = await db.rawQuery(
      'SELECT status, COALESCE(SUM(amount), 0) as total FROM payouts GROUP BY status',
    );

    return {
      'revenue': totalRevenue.first['total'] ?? 0.0,
      'customers': totalCustomers.first['total'] ?? 0,
      'kycCount': kycCount,
      'payouts': payouts,
    };
  }

  @override
  Future<void> addPayout({
    required String merchant,
    required double amount,
  }) async {
    await initialize();
    await _database!.insert('payouts', {
      'merchant': merchant.trim(),
      'amount': amount,
      'status': 'Pending',
      'paidAt': DateTime.now().toIso8601String(),
    });
  }

  @override
  Future<void> approvePayout(int id) async {
    await initialize();
    await _database!.update(
      'payouts',
      {'status': 'Approved'},
      where: 'id = ? AND status = ?',
      whereArgs: [id, 'Pending'],
    );
  }

  @override
  Future<String> exportPayoutCsv() async {
    final rows = await getPayoutRows();
    final buffer = StringBuffer();

    buffer.writeln('merchant,amount,status,paidAt');
    for (final row in rows) {
      final merchant = _escapeCsv((row['merchant'] ?? '').toString());
      final amount = (row['amount'] as num? ?? 0).toString();
      final status = _escapeCsv((row['status'] ?? '').toString());
      final paidAt = _escapeCsv((row['paidAt'] ?? '').toString());

      buffer.writeln('$merchant,$amount,$status,$paidAt');
    }

    return buffer.toString();
  }

  Future<void> _onCreate(Database db, int version) async {
    await db.execute('''
      CREATE TABLE kyc (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT,
        status TEXT,
        updatedAt TEXT
      )
    ''');

    await db.execute('''
      CREATE TABLE payouts (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        merchant TEXT,
        amount REAL,
        status TEXT,
        paidAt TEXT
      )
    ''');

    await db.execute('''
      CREATE TABLE orders (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        customer TEXT,
        total REAL,
        status TEXT,
        createdAt TEXT
      )
    ''');

    await db.execute('''
      CREATE TABLE customers (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT,
        city TEXT,
        orders INTEGER,
        spend REAL
      )
    ''');

    await db.execute('''
      CREATE TABLE admin_users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        username TEXT UNIQUE,
        passwordHash TEXT,
        role TEXT
      )
    ''');

    final now = DateTime.now().toIso8601String();

    await db.insert('kyc', {'name': 'Aisha Rahman', 'status': 'Approved', 'updatedAt': now});
    await db.insert('kyc', {'name': 'Daniel Kim', 'status': 'Pending', 'updatedAt': now});
    await db.insert('kyc', {'name': 'Nadia Bello', 'status': 'Rejected', 'updatedAt': now});

    await db.insert('payouts', {'merchant': 'Fresh Laundry Co.', 'amount': 3450.00, 'status': 'Paid', 'paidAt': now});
    await db.insert('payouts', {'merchant': 'Bubbles Wash', 'amount': 1875.50, 'status': 'Pending', 'paidAt': now});
    await db.insert('payouts', {'merchant': 'Spark Clean', 'amount': 2750.00, 'status': 'Approved', 'paidAt': now});

    await db.insert('orders', {'customer': 'Aisha Rahman', 'total': 420.00, 'status': 'Completed', 'createdAt': now});
    await db.insert('orders', {'customer': 'Daniel Kim', 'total': 280.00, 'status': 'In progress', 'createdAt': now});

    await db.insert('customers', {'name': 'Aisha Rahman', 'city': 'Kigali', 'orders': 14, 'spend': 9400.00});
    await db.insert('customers', {'name': 'Daniel Kim', 'city': 'Nairobi', 'orders': 8, 'spend': 6200.00});
    await db.insert('customers', {'name': 'Nadia Bello', 'city': 'Accra', 'orders': 11, 'spend': 7800.00});
  }

  Future<void> _onUpgrade(Database db, int oldVersion, int newVersion) async {
    if (oldVersion < 2) {
      await db.execute('''
        CREATE TABLE IF NOT EXISTS admin_users (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          username TEXT UNIQUE,
          passwordHash TEXT,
          role TEXT
        )
      ''');
    }
  }

  Future<void> _ensureUsers() async {
    final db = _database!;
    final existing = await db.query('admin_users');

    if (existing.isNotEmpty) return;

    final adminHash = sha256.convert(utf8.encode('admin123')).toString();
    final staffHash = sha256.convert(utf8.encode('staff123')).toString();

    await db.insert('admin_users', {
      'username': 'admin',
      'passwordHash': adminHash,
      'role': 'admin',
    });

    await db.insert('admin_users', {
      'username': 'staff',
      'passwordHash': staffHash,
      'role': 'staff',
    });
  }

  String _escapeCsv(String value) {
    final escaped = value.replaceAll('"', '""');
    return '"$escaped"';
  }
}

class SupabaseLaundryDatabase implements LaundryDatabase {
  SupabaseClient get _client => Supabase.instance.client;

  @override
  Future<void> initialize() async {
    await Supabase.initialize(
      url: AppConfig.supabaseUrl,
      publishableKey: AppConfig.supabaseAnonKey,
    );
  }

  @override
  Future<Map<String, dynamic>?> login({
    required String username,
    required String password,
  }) async {
    final data = await _client
        .from('admin_users')
        .select()
        .eq('username', username.trim())
        .limit(1)
        .maybeSingle();

    if (data == null) return null;

    final hash = sha256.convert(utf8.encode(password)).toString();
    if ((data['password_hash'] ?? '') != hash) return null;

    return {
      'username': data['username'],
      'role': data['role'] ?? 'staff',
    };
  }

  @override
  Future<List<Map<String, dynamic>>> getKycRows() async {
    final resp = await _client.from('kyc').select();
    return (resp as List).map((row) => {
          ...Map<String, dynamic>.from(row as Map),
          'updatedAt': row['updated_at'],
        }).toList();
  }

  @override
  Future<List<Map<String, dynamic>>> getPayoutRows() async {
    final resp = await _client.from('payouts').select();
    return (resp as List).map((row) => {
          ...Map<String, dynamic>.from(row as Map),
          'paidAt': row['paid_at'],
        }).toList();
  }

  @override
  Future<List<Map<String, dynamic>>> getOrderRows() async {
    final resp = await _client.from('orders').select();
    return (resp as List).map((row) => {
          ...Map<String, dynamic>.from(row as Map),
          'createdAt': row['created_at'],
        }).toList();
  }

  @override
  Future<List<Map<String, dynamic>>> getCustomerRows() async {
    final resp = await _client.from('customers').select();
    return (resp as List).map((row) => Map<String, dynamic>.from(row as Map)).toList();
  }

  @override
  Future<Map<String, dynamic>> getDashboardSummary() async {
    final revenue = await _client.from('orders').select('total');
    final count = await _client.from('customers').select('id');
    final kyc = await _client.from('kyc').select('status');
    final payouts = await _client.from('payouts').select('status,amount');

    return {
      'revenue': _sum(revenue, 'total'),
      'customers': (count as List).length,
      'kycCount': kyc,
      'payouts': payouts,
    };
  }

  @override
  Future<void> addPayout({
    required String merchant,
    required double amount,
  }) async {
    await _client.from('payouts').insert({
      'merchant': merchant.trim(),
      'amount': amount,
      'status': 'Pending',
      'paid_at': DateTime.now().toIso8601String(),
    });
  }

  @override
  Future<void> approvePayout(int id) async {
    await _client
        .from('payouts')
        .update({'status': 'Approved'})
        .eq('id', id)
        .eq('status', 'Pending');
  }

  @override
  Future<String> exportPayoutCsv() async {
    final rows = await getPayoutRows();
    final buffer = StringBuffer();
    buffer.writeln('merchant,amount,status,paidAt');
    for (final row in rows) {
      buffer.writeln(
        [
          '"${(row['merchant'] ?? '').toString().replaceAll('"', '""')}"',
          (row['amount'] as num? ?? 0).toString(),
          '"${(row['status'] ?? '').toString().replaceAll('"', '""')}"',
          '"${(row['paidAt'] ?? '').toString().replaceAll('"', '""')}"',
        ].join(','),
      );
    }
    return buffer.toString();
  }

  double _sum(List data, String key) {
    double total = 0;
    for (final item in data) {
      if (item is Map && item.containsKey(key)) {
        total += (item[key] as num? ?? 0).toDouble();
      }
    }
    return total;
  }
}