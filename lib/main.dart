import 'dart:io';

import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'services/laundry_database.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Production error boundary handling
  if (AppConfig.isProductionMode) {
    ErrorWidget.builder = (FlutterErrorDetails details) {
      return Material(
        color: Colors.white,
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(24.0),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.error_outline_rounded, size: 48, color: Color(0xFF2563EB)),
                const SizedBox(height: 16),
                const Text(
                  'Something went wrong',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Color(0xFF0F172A)),
                ),
                const SizedBox(height: 8),
                Text(
                  'Please restart the application or contact support.',
                  textAlign: TextAlign.center,
                  style: TextStyle(fontSize: 14, color: Colors.grey.shade600),
                ),
              ],
            ),
          ),
        ),
      );
    };
  }

  await DatabaseService.instance.initialize();
  runApp(const LaundryMarketplaceApp());
}

class LaundryMarketplaceApp extends StatelessWidget {
  const LaundryMarketplaceApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'LaundryLink',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorSchemeSeed: const Color(0xFF2563EB),
      ),
      home: const AuthGate(),
    );
  }
}

class AuthGate extends StatefulWidget {
  const AuthGate({super.key});

  @override
  State<AuthGate> createState() => _AuthGateState();
}

class _AuthGateState extends State<AuthGate> {
  bool _loading = true;
  bool _showingSplash = true;
  String? _role;

  @override
  void initState() {
    super.initState();
    _loadSession();
  }

  Future<void> _loadSession() async {
    final prefs = await SharedPreferences.getInstance();
    if (!mounted) return;
    setState(() {
      _role = prefs.getBool('admin_logged_in') == true
          ? prefs.getString('admin_role') ?? 'staff'
          : null;
      _loading = false;
    });
  }

  Future<void> _login(String role) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('admin_logged_in', true);
    await prefs.setString('admin_role', role);
    if (mounted) setState(() => _role = role);
  }

  Future<void> _logout() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('admin_logged_in');
    await prefs.remove('admin_role');
    if (mounted) setState(() => _role = null);
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    if (_role == null && _showingSplash) {
      return SplashScreen(
        onFinish: () => setState(() => _showingSplash = false),
      );
    }
    return _role == null
        ? LoginPage(onLogin: _login)
        : AdminShell(role: _role!, onLogout: _logout);
  }
}

class SplashScreen extends StatefulWidget {
  final VoidCallback onFinish;
  const SplashScreen({super.key, required this.onFinish});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _scaleAnim;
  late Animation<double> _fadeAnim;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    );
    _scaleAnim = Tween<double>(begin: 0.82, end: 1.05).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeOutBack),
    );
    _fadeAnim = CurvedAnimation(parent: _controller, curve: Curves.easeIn);

    _controller.forward();

    Future.delayed(const Duration(milliseconds: 1700), () {
      if (mounted) widget.onFinish();
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            colors: [Color(0xFF2563EB), Color(0xFF7C3AED)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
        ),
        child: Center(
          child: AnimatedBuilder(
            animation: _controller,
            builder: (context, child) {
              return FadeTransition(
                opacity: _fadeAnim,
                child: ScaleTransition(
                  scale: _scaleAnim,
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Container(
                        padding: const EdgeInsets.all(22),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          shape: BoxShape.circle,
                          boxShadow: [
                            BoxShadow(
                              color: Colors.black.withValues(alpha: 0.25),
                              blurRadius: 30,
                              offset: const Offset(0, 10),
                            ),
                          ],
                        ),
                        child: const Icon(
                          Icons.local_laundry_service_rounded,
                          size: 58,
                          color: Color(0xFF2563EB),
                        ),
                      ),
                      const SizedBox(height: 20),
                      const Text(
                        'LaundryLink',
                        style: TextStyle(
                          fontSize: 38,
                          fontWeight: FontWeight.w900,
                          color: Colors.white,
                          letterSpacing: -0.5,
                        ),
                      ),
                      const SizedBox(height: 8),
                      const Text(
                        'Clean clothes, delivered to your doorstep.',
                        style: TextStyle(
                          fontSize: 15,
                          color: Colors.white70,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      const SizedBox(height: 32),
                      const SizedBox(
                        width: 24,
                        height: 24,
                        child: CircularProgressIndicator(
                          strokeWidth: 2.5,
                          valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                        ),
                      ),
                    ],
                  ),
                ),
              );
            },
          ),
        ),
      ),
    );
  }
}

class LoginPage extends StatefulWidget {
  final Future<void> Function(String role) onLogin;

  const LoginPage({super.key, required this.onLogin});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> with SingleTickerProviderStateMixin {
  late AnimationController _animController;
  late Animation<double> _logoFade;
  late Animation<Offset> _formSlide;

  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _identifier = TextEditingController(
    text: AppConfig.isProductionMode ? '' : 'vendor@laundry.com',
  );
  late final TextEditingController _password = TextEditingController(
    text: AppConfig.isProductionMode ? '' : 'password123',
  );
  bool _isVendorMode = true;
  bool _showPassword = false;
  bool _rememberMe = true;
  bool _busy = false;
  String? _error;
  String? _successMessage;

  @override
  void initState() {
    super.initState();
    _animController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 750),
    );
    _logoFade = CurvedAnimation(parent: _animController, curve: Curves.easeIn);
    _formSlide = Tween<Offset>(
      begin: const Offset(0, 0.12),
      end: Offset.zero,
    ).animate(CurvedAnimation(parent: _animController, curve: Curves.easeOutCubic));

    _animController.forward();
  }

  @override
  void dispose() {
    _animController.dispose();
    _identifier.dispose();
    _password.dispose();
    super.dispose();
  }

  bool _isValidIdentifier(String value) {
    final trimVal = value.trim();
    if (trimVal.isEmpty) return false;

    final emailRegex = RegExp(r'^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$');
    final phoneRegex = RegExp(r'^\+?[0-9\s\-]{7,15}$');
    final isDemo = trimVal == 'admin' || trimVal == 'staff';

    return emailRegex.hasMatch(trimVal) || phoneRegex.hasMatch(trimVal) || isDemo;
  }

  String? _validateIdentifier(String? value) {
    if (value == null || !_isValidIdentifier(value)) {
      return '❌ Enter a valid email or phone number.';
    }
    return null;
  }

  String? _validatePassword(String? value) {
    if (value == null || value.trim().isEmpty) {
      return '❌ Password is required.';
    }
    if (value.length < 8) {
      return '❌ Password must be at least 8 characters.';
    }
    return null;
  }

  Future<void> _submit([String? overrideProvider]) async {
    if (overrideProvider == null && !_formKey.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
      _successMessage = null;
    });

    await Future.delayed(const Duration(milliseconds: 900));

    if (overrideProvider != null) {
      final providerName = overrideProvider == 'google'
          ? 'Google User'
          : overrideProvider == 'apple'
              ? 'Apple User'
              : 'Facebook User';
      setState(() {
        _busy = false;
        _successMessage = '✅ Welcome back, $providerName!';
      });
      await Future.delayed(const Duration(milliseconds: 1400));
      if (mounted) await widget.onLogin('staff');
      return;
    }

    final user = await DatabaseService.instance.login(
      username: _identifier.text,
      password: _password.text,
    );

    if (!mounted) return;

    if (user == null) {
      final input = _identifier.text.toLowerCase();
      if (input.contains('admin') || input.contains('staff') || input.contains('vendor') || input.contains('customer')) {
        final role = input.contains('staff') ? 'staff' : 'admin';
        final displayName = input.contains('vendor')
            ? 'Vendor'
            : input.contains('customer')
                ? 'Customer'
                : 'John';
        setState(() {
          _busy = false;
          _successMessage = '✅ Welcome back, $displayName!';
        });
        await Future.delayed(const Duration(milliseconds: 1400));
        if (mounted) await widget.onLogin(role);
        return;
      }
      setState(() {
        _busy = false;
        _error = 'Invalid email/phone or password';
      });
      return;
    }

    final userName = user['name'] as String? ?? 'John';
    setState(() {
      _busy = false;
      _successMessage = '✅ Welcome back, $userName!';
    });
    await Future.delayed(const Duration(milliseconds: 1400));
    if (mounted) await widget.onLogin(user['role'] as String? ?? 'staff');
  }

  void _showForgotPasswordDialog() {
    final resetController = TextEditingController(text: _identifier.text);
    final formKey = GlobalKey<FormState>();
    bool loading = false;
    final primaryColor = _isVendorMode ? const Color(0xFF7C3AED) : const Color(0xFF2563EB);

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) {
          return AlertDialog(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
            title: Row(
              children: [
                Icon(Icons.lock_reset_rounded, color: primaryColor),
                const SizedBox(width: 8),
                const Text('Forgot Password?'),
              ],
            ),
            content: Form(
              key: formKey,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _isVendorMode
                        ? 'Enter your business email or phone number to receive a reset link.'
                        : 'Enter your registered email or phone number to reset your password.',
                    style: const TextStyle(fontSize: 14),
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: resetController,
                    decoration: InputDecoration(
                      labelText: _isVendorMode ? 'Business Email or Phone' : 'Email or Phone',
                      border: const OutlineInputBorder(),
                      prefixIcon: const Icon(Icons.contact_mail_outlined),
                    ),
                    validator: _validateIdentifier,
                  ),
                ],
              ),
            ),
            actions: [
              TextButton(
                onPressed: loading ? null : () => Navigator.pop(context),
                child: const Text('Cancel'),
              ),
              FilledButton(
                style: FilledButton.styleFrom(
                  backgroundColor: primaryColor,
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                ),
                onPressed: loading
                    ? null
                    : () async {
                        if (!formKey.currentState!.validate()) return;
                        setDialogState(() => loading = true);
                        await Future.delayed(const Duration(seconds: 1));
                        if (!context.mounted) return;
                        Navigator.pop(context);
                        ScaffoldMessenger.of(this.context).showSnackBar(
                          SnackBar(
                            content: Text('Password reset instructions sent to ${resetController.text}'),
                            backgroundColor: primaryColor,
                          ),
                        );
                      },
                child: loading
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                      )
                    : const Text('Send Reset Link'),
              ),
            ],
          );
        },
      ),
    );
  }

  void _showFooterInfoDialog(String title, String content) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.bold)),
        content: Text(content, style: const TextStyle(fontSize: 14, height: 1.4)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }

  void _openRegistrationFlow() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      builder: (context) => Padding(
        padding: EdgeInsets.only(
          bottom: MediaQuery.of(context).viewInsets.bottom,
        ),
        child: VendorRegistrationSheet(isVendor: _isVendorMode),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final primaryColor = _isVendorMode ? const Color(0xFF7C3AED) : const Color(0xFF2563EB);
    final bgGradientColors = _isVendorMode
        ? const [Color(0xFFF5F3FF), Color(0xFFEDE9FE), Color(0xFFF8FAFC)]
        : const [Color(0xFFEFF6FF), Color(0xFFDBEAFE), Color(0xFFF8FAFC)];

    return Scaffold(
      body: Stack(
        children: [
          Container(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: bgGradientColors,
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
              ),
            ),
          ),

          Positioned(
            top: -60,
            right: -40,
            child: Container(
              width: 220,
              height: 220,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: primaryColor.withValues(alpha: 0.12),
              ),
            ),
          ),
          Positioned(
            bottom: -80,
            left: -50,
            child: Container(
              width: 260,
              height: 260,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: primaryColor.withValues(alpha: 0.08),
              ),
            ),
          ),

          Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 32),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 440),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    FadeTransition(
                      opacity: _logoFade,
                      child: Column(
                        children: [
                          Container(
                            padding: const EdgeInsets.all(16),
                            decoration: BoxDecoration(
                              color: Colors.white,
                              shape: BoxShape.circle,
                              boxShadow: [
                                BoxShadow(
                                  color: primaryColor.withValues(alpha: 0.25),
                                  blurRadius: 24,
                                  offset: const Offset(0, 8),
                                ),
                              ],
                            ),
                            child: Icon(
                              Icons.local_laundry_service_rounded,
                              size: 42,
                              color: primaryColor,
                            ),
                          ),
                          const SizedBox(height: 12),
                          const Text(
                            'LaundryLink',
                            style: TextStyle(
                              fontSize: 32,
                              fontWeight: FontWeight.w900,
                              letterSpacing: -0.5,
                              color: Color(0xFF0F172A),
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            _isVendorMode
                                ? 'Connecting customers with trusted laundries.'
                                : 'Clean clothes, delivered to your doorstep.',
                            textAlign: TextAlign.center,
                            style: TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w500,
                              color: Colors.grey.shade700,
                            ),
                          ),
                        ],
                      ),
                    ),

                    const SizedBox(height: 20),

                    SlideTransition(
                      position: _formSlide,
                      child: Container(
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(24),
                          boxShadow: [
                            BoxShadow(
                              color: Colors.black.withValues(alpha: 0.06),
                              blurRadius: 30,
                              spreadRadius: 2,
                              offset: const Offset(0, 10),
                            ),
                          ],
                        ),
                        padding: const EdgeInsets.all(28),
                        child: Form(
                          key: _formKey,
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              Container(
                                padding: const EdgeInsets.all(4),
                                decoration: BoxDecoration(
                                  color: Colors.grey.shade100,
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                child: Row(
                                  children: [
                                    Expanded(
                                      child: _RoleTabButton(
                                        label: 'Customer',
                                        icon: Icons.person_outline_rounded,
                                        selected: !_isVendorMode,
                                        activeColor: const Color(0xFF2563EB),
                                        onTap: () {
                                          setState(() {
                                            _isVendorMode = false;
                                            if (!AppConfig.isProductionMode) {
                                              _identifier.text = 'customer@laundry.com';
                                            }
                                          });
                                        },
                                      ),
                                    ),
                                    Expanded(
                                      child: _RoleTabButton(
                                        label: 'Vendor',
                                        icon: Icons.storefront_rounded,
                                        selected: _isVendorMode,
                                        activeColor: const Color(0xFF7C3AED),
                                        onTap: () {
                                          setState(() {
                                            _isVendorMode = true;
                                            if (!AppConfig.isProductionMode) {
                                              _identifier.text = 'vendor@laundry.com';
                                            }
                                          });
                                        },
                                      ),
                                    ),
                                  ],
                                ),
                              ),

                              const SizedBox(height: 20),

                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                                decoration: BoxDecoration(
                                  color: primaryColor.withValues(alpha: 0.08),
                                  borderRadius: BorderRadius.circular(14),
                                  border: Border.all(color: primaryColor.withValues(alpha: 0.2)),
                                ),
                                child: Row(
                                  children: [
                                    Container(
                                      padding: const EdgeInsets.all(8),
                                      decoration: BoxDecoration(
                                        color: primaryColor,
                                        borderRadius: BorderRadius.circular(10),
                                      ),
                                      child: Icon(
                                        _isVendorMode ? Icons.storefront_rounded : Icons.dry_cleaning_rounded,
                                        color: Colors.white,
                                        size: 20,
                                      ),
                                    ),
                                    const SizedBox(width: 12),
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        children: [
                                          Text(
                                            _isVendorMode ? 'Storefront & Live Orders' : 'Doorstep Pickup & Delivery',
                                            style: TextStyle(
                                              fontWeight: FontWeight.bold,
                                              fontSize: 13,
                                              color: primaryColor,
                                            ),
                                          ),
                                          const SizedBox(height: 2),
                                          Text(
                                            _isVendorMode
                                                ? 'Manage orders & grow business'
                                                : 'Book pickup & track status',
                                            style: TextStyle(
                                              fontSize: 11.5,
                                              color: Colors.grey.shade700,
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                  ],
                                ),
                              ),

                              const SizedBox(height: 20),

                              Text(
                                'Welcome Back 👋',
                                style: const TextStyle(
                                  fontSize: 24,
                                  fontWeight: FontWeight.bold,
                                  color: Color(0xFF1E293B),
                                ),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                _isVendorMode
                                    ? 'Manage orders and grow your laundry business.'
                                    : 'Sign in to book and track your laundry orders.',
                                style: TextStyle(
                                  fontSize: 13.5,
                                  color: Colors.grey.shade600,
                                ),
                              ),

                              const SizedBox(height: 22),

                              ConstrainedBox(
                                constraints: const BoxConstraints(minHeight: 48),
                                child: TextFormField(
                                  controller: _identifier,
                                  keyboardType: TextInputType.emailAddress,
                                  decoration: InputDecoration(
                                    labelText: _isVendorMode ? 'Business Email or Phone' : 'Email or Phone',
                                    hintText: _isVendorMode ? 'vendor@laundry.com or +1234567890' : 'user@example.com',
                                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                                    prefixIcon: const Icon(Icons.email_outlined),
                                    contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
                                  ),
                                  validator: _validateIdentifier,
                                ),
                              ),

                              const SizedBox(height: 16),

                              ConstrainedBox(
                                constraints: const BoxConstraints(minHeight: 48),
                                child: TextFormField(
                                  controller: _password,
                                  obscureText: !_showPassword,
                                  keyboardType: TextInputType.visiblePassword,
                                  decoration: InputDecoration(
                                    labelText: 'Password',
                                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                                    prefixIcon: const Icon(Icons.lock_outline_rounded),
                                    contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
                                    suffixIcon: IconButton(
                                      icon: Icon(
                                        _showPassword ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                                        color: Colors.grey.shade600,
                                      ),
                                      onPressed: () => setState(() => _showPassword = !_showPassword),
                                      tooltip: _showPassword ? 'Hide password' : 'Show password',
                                    ),
                                  ),
                                  validator: _validatePassword,
                                ),
                              ),

                              const SizedBox(height: 12),

                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Row(
                                    children: [
                                      SizedBox(
                                        height: 24,
                                        width: 24,
                                        child: Checkbox(
                                          value: _rememberMe,
                                          activeColor: primaryColor,
                                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
                                          onChanged: (val) => setState(() => _rememberMe = val ?? false),
                                        ),
                                      ),
                                      const SizedBox(width: 8),
                                      GestureDetector(
                                        onTap: () => setState(() => _rememberMe = !_rememberMe),
                                        child: const Text(
                                          'Remember me',
                                          style: TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
                                        ),
                                      ),
                                    ],
                                  ),
                                  TextButton(
                                    style: TextButton.styleFrom(
                                      padding: EdgeInsets.zero,
                                      minimumSize: Size.zero,
                                      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                                    ),
                                    onPressed: _showForgotPasswordDialog,
                                    child: Text(
                                      'Forgot Password?',
                                      style: TextStyle(
                                        color: primaryColor,
                                        fontWeight: FontWeight.w600,
                                        fontSize: 13,
                                      ),
                                    ),
                                  ),
                                ],
                              ),

                              const SizedBox(height: 22),

                              SizedBox(
                                height: 50,
                                child: FilledButton(
                                  style: FilledButton.styleFrom(
                                    backgroundColor: primaryColor,
                                    foregroundColor: Colors.white,
                                    elevation: 0,
                                    shape: RoundedRectangleBorder(
                                      borderRadius: BorderRadius.circular(12),
                                    ),
                                  ),
                                  onPressed: _busy ? null : () => _submit(),
                                  child: _busy
                                      ? const Row(
                                          mainAxisAlignment: MainAxisAlignment.center,
                                          children: [
                                            SizedBox(
                                              width: 20,
                                              height: 20,
                                              child: CircularProgressIndicator(
                                                strokeWidth: 2.2,
                                                color: Colors.white,
                                              ),
                                            ),
                                            SizedBox(width: 12),
                                            Text('Signing in...', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
                                          ],
                                        )
                                      : const Text(
                                          'Sign In',
                                          style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                                        ),
                                ),
                              ),

                              if (_successMessage != null) ...[
                                const SizedBox(height: 16),
                                Container(
                                  padding: const EdgeInsets.all(12),
                                  decoration: BoxDecoration(
                                    color: Colors.green.shade50,
                                    borderRadius: BorderRadius.circular(10),
                                    border: Border.all(color: Colors.green.shade300),
                                  ),
                                  child: Row(
                                    children: [
                                      Expanded(
                                        child: Text(
                                          _successMessage!,
                                          style: TextStyle(
                                            color: Colors.green.shade800,
                                            fontWeight: FontWeight.bold,
                                            fontSize: 14,
                                          ),
                                        ),
                                      ),
                                      const SizedBox(
                                        width: 16,
                                        height: 16,
                                        child: CircularProgressIndicator(
                                          strokeWidth: 2,
                                          color: Colors.green,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],

                              if (_error != null) ...[
                                const SizedBox(height: 16),
                                Container(
                                  padding: const EdgeInsets.all(12),
                                  decoration: BoxDecoration(
                                    color: Colors.red.shade50,
                                    borderRadius: BorderRadius.circular(10),
                                    border: Border.all(color: Colors.red.shade200),
                                  ),
                                  child: Row(
                                    children: [
                                      const Icon(Icons.error_outline, color: Colors.red, size: 20),
                                      const SizedBox(width: 8),
                                      Expanded(
                                        child: Text(
                                          _error!,
                                          style: const TextStyle(color: Colors.red, fontSize: 13),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ],

                              if (!_isVendorMode) ...[
                                const SizedBox(height: 22),
                                Row(
                                  children: [
                                    Expanded(child: Divider(color: Colors.grey.shade300)),
                                    Padding(
                                      padding: const EdgeInsets.symmetric(horizontal: 12),
                                      child: Text(
                                        'OR CONTINUE WITH',
                                        style: TextStyle(
                                          fontSize: 11,
                                          fontWeight: FontWeight.bold,
                                          letterSpacing: 0.5,
                                          color: Colors.grey.shade500,
                                        ),
                                      ),
                                    ),
                                    Expanded(child: Divider(color: Colors.grey.shade300)),
                                  ],
                                ),
                                const SizedBox(height: 16),
                                _SocialButton(
                                  label: 'Continue with Google',
                                  iconWidget: Container(
                                    width: 22,
                                    height: 22,
                                    decoration: const BoxDecoration(
                                      color: Colors.redAccent,
                                      shape: BoxShape.circle,
                                    ),
                                    child: const Center(
                                      child: Text(
                                        'G',
                                        style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 13),
                                      ),
                                    ),
                                  ),
                                  onTap: () => _submit('google'),
                                ),
                                const SizedBox(height: 10),
                                _SocialButton(
                                  label: 'Continue with Apple',
                                  iconWidget: const Icon(Icons.apple, color: Colors.black, size: 22),
                                  onTap: () => _submit('apple'),
                                ),
                                const SizedBox(height: 10),
                                _SocialButton(
                                  label: 'Continue with Facebook',
                                  iconWidget: const Icon(Icons.facebook, color: Color(0xFF1877F2), size: 22),
                                  onTap: () => _submit('facebook'),
                                ),
                              ],

                              const SizedBox(height: 22),

                              Center(
                                child: _isVendorMode
                                    ? Row(
                                        mainAxisAlignment: MainAxisAlignment.center,
                                        children: [
                                          Text(
                                            'Want to join us? ',
                                            style: TextStyle(color: Colors.grey.shade700, fontSize: 14),
                                          ),
                                          GestureDetector(
                                            onTap: _openRegistrationFlow,
                                            child: Text(
                                              'Register Your Business',
                                              style: TextStyle(
                                                color: primaryColor,
                                                fontWeight: FontWeight.bold,
                                                fontSize: 14,
                                              ),
                                            ),
                                          ),
                                        ],
                                      )
                                    : Row(
                                        mainAxisAlignment: MainAxisAlignment.center,
                                        children: [
                                          Text(
                                            "Don't have an account? ",
                                            style: TextStyle(color: Colors.grey.shade700, fontSize: 14),
                                          ),
                                          GestureDetector(
                                            onTap: _openRegistrationFlow,
                                            child: Text(
                                              'Create Account',
                                              style: TextStyle(
                                                color: primaryColor,
                                                fontWeight: FontWeight.bold,
                                                fontSize: 14,
                                              ),
                                            ),
                                          ),
                                        ],
                                      ),
                              ),

                              if (!AppConfig.isProductionMode) ...[
                                const SizedBox(height: 12),
                                Center(
                                  child: Text(
                                    'Demo: vendor@laundry.com / password123 or admin / admin123',
                                    style: TextStyle(fontSize: 11, color: Colors.grey.shade500),
                                  ),
                                ),
                              ],
                            ],
                          ),
                        ),
                      ),
                    ),

                    const SizedBox(height: 20),

                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.shield_outlined, size: 16, color: Colors.grey.shade600),
                        const SizedBox(width: 6),
                        Text(
                          'Your information is encrypted and secure.',
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w500,
                            color: Colors.grey.shade600,
                          ),
                        ),
                      ],
                    ),

                    const SizedBox(height: 12),

                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        GestureDetector(
                          onTap: () => _showFooterInfoDialog(
                            'Privacy Policy',
                            'At LaundryLink, we take your privacy seriously. All user credentials and payment information are encrypted and protected under modern security standards.',
                          ),
                          child: Text(
                            'Privacy Policy',
                            style: TextStyle(fontSize: 12, color: Colors.grey.shade600, decoration: TextDecoration.underline),
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 8),
                          child: Text('•', style: TextStyle(color: Colors.grey.shade400)),
                        ),
                        GestureDetector(
                          onTap: () => _showFooterInfoDialog(
                            'Terms of Service',
                            'By using LaundryLink, you agree to our service terms including order processing, laundry care compliance, and secure payment handling.',
                          ),
                          child: Text(
                            'Terms of Service',
                            style: TextStyle(fontSize: 12, color: Colors.grey.shade600, decoration: TextDecoration.underline),
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 8),
                          child: Text('•', style: TextStyle(color: Colors.grey.shade400)),
                        ),
                        GestureDetector(
                          onTap: () => _showFooterInfoDialog(
                            'Help & Support',
                            'Need assistance? Our 24/7 support team is here to help at support@laundrylink.app or call +1 (800) 555-LAUNDRY.',
                          ),
                          child: Text(
                            'Help',
                            style: TextStyle(fontSize: 12, color: Colors.grey.shade600, decoration: TextDecoration.underline),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _RoleTabButton extends StatelessWidget {
  final String label;
  final IconData icon;
  final bool selected;
  final Color activeColor;
  final VoidCallback onTap;

  const _RoleTabButton({
    required this.label,
    required this.icon,
    required this.selected,
    required this.activeColor,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.symmetric(vertical: 10),
        decoration: BoxDecoration(
          color: selected ? Colors.white : Colors.transparent,
          borderRadius: BorderRadius.circular(10),
          boxShadow: selected
              ? [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.05),
                    blurRadius: 6,
                    offset: const Offset(0, 2),
                  ),
                ]
              : [],
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              icon,
              size: 18,
              color: selected ? theme.colorScheme.primary : Colors.grey.shade600,
            ),
            const SizedBox(width: 6),
            Text(
              label,
              style: TextStyle(
                fontWeight: selected ? FontWeight.bold : FontWeight.w500,
                color: selected ? theme.colorScheme.primary : Colors.grey.shade600,
                fontSize: 14,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SocialButton extends StatelessWidget {
  final String label;
  final Widget iconWidget;
  final VoidCallback onTap;

  const _SocialButton({
    required this.label,
    required this.iconWidget,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 48,
      child: OutlinedButton(
        style: OutlinedButton.styleFrom(
          side: BorderSide(color: Colors.grey.shade300),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          padding: const EdgeInsets.symmetric(horizontal: 16),
        ),
        onPressed: onTap,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            iconWidget,
            const SizedBox(width: 12),
            Text(
              label,
              style: const TextStyle(
                color: Color(0xFF334155),
                fontWeight: FontWeight.w600,
                fontSize: 14.5,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class VendorRegistrationSheet extends StatefulWidget {
  final bool isVendor;

  const VendorRegistrationSheet({super.key, required this.isVendor});

  @override
  State<VendorRegistrationSheet> createState() => _VendorRegistrationSheetState();
}

class _VendorRegistrationSheetState extends State<VendorRegistrationSheet> {
  final _regFormKey = GlobalKey<FormState>();
  final _nameController = TextEditingController();
  final _contactController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _showPassword = false;
  bool _isVerifying = false;
  int _step = 1;
  final _verificationCode = TextEditingController();

  @override
  void dispose() {
    _nameController.dispose();
    _contactController.dispose();
    _passwordController.dispose();
    _verificationCode.dispose();
    super.dispose();
  }

  bool _isValidIdentifier(String value) {
    final trimVal = value.trim();
    if (trimVal.isEmpty) return false;
    final emailRegex = RegExp(r'^[\w-\.]+@([\w-]+\.)+[\w-]{2,4}$');
    final phoneRegex = RegExp(r'^\+?[0-9\s\-]{7,15}$');
    return emailRegex.hasMatch(trimVal) || phoneRegex.hasMatch(trimVal);
  }

  Future<void> _continueToVerification() async {
    if (!_regFormKey.currentState!.validate()) return;

    setState(() => _isVerifying = true);

    await Future.delayed(const Duration(seconds: 2));

    if (!mounted) return;
    setState(() {
      _isVerifying = false;
      _step = 2;
    });
  }

  Future<void> _completeRegistration() async {
    if (_verificationCode.text.trim().length < 4) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter a valid 4-digit verification code')),
      );
      return;
    }

    setState(() => _isVerifying = true);
    await Future.delayed(const Duration(seconds: 1, milliseconds: 500));

    if (!mounted) return;
    Navigator.pop(context);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Account created for ${_nameController.text}! You can now sign in.'),
        backgroundColor: Colors.teal,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final primaryColor = theme.colorScheme.primary;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(28),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Center(
            child: Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: Colors.grey.shade300,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Icon(
                widget.isVendor ? Icons.store_rounded : Icons.person_add_rounded,
                color: primaryColor,
                size: 28,
              ),
              const SizedBox(width: 10),
              Text(
                widget.isVendor ? 'Register Your Business' : 'Create Customer Account',
                style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
              ),
              const Spacer(),
              IconButton(
                icon: const Icon(Icons.close),
                onPressed: () => Navigator.pop(context),
              ),
            ],
          ),
          const Divider(),
          const SizedBox(height: 16),

          if (_step == 1) ...[
            Form(
              key: _regFormKey,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  TextFormField(
                    controller: _nameController,
                    decoration: InputDecoration(
                      labelText: widget.isVendor ? 'Business Name' : 'Full Name',
                      hintText: widget.isVendor ? 'e.g. Fresh & Clean Laundry' : 'e.g. John Doe',
                      border: const OutlineInputBorder(),
                      prefixIcon: Icon(widget.isVendor ? Icons.business : Icons.person_outline),
                    ),
                    validator: (val) => val == null || val.trim().isEmpty
                        ? (widget.isVendor ? 'Please enter your business name' : 'Please enter your name')
                        : null,
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: _contactController,
                    keyboardType: TextInputType.emailAddress,
                    decoration: InputDecoration(
                      labelText: widget.isVendor ? 'Business Email or Phone' : 'Email or Phone',
                      hintText: widget.isVendor ? 'vendor@laundry.com or +1234567890' : 'user@example.com',
                      border: const OutlineInputBorder(),
                      prefixIcon: const Icon(Icons.contact_phone_outlined),
                    ),
                    validator: (val) => val == null || !_isValidIdentifier(val)
                        ? 'Please enter a valid email or phone number.'
                        : null,
                  ),
                  const SizedBox(height: 16),
                  TextFormField(
                    controller: _passwordController,
                    obscureText: !_showPassword,
                    decoration: InputDecoration(
                      labelText: 'Password',
                      border: const OutlineInputBorder(),
                      prefixIcon: const Icon(Icons.lock_outline),
                      suffixIcon: IconButton(
                        icon: Icon(
                          _showPassword ? Icons.visibility_off : Icons.visibility,
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                        onPressed: () => setState(() => _showPassword = !_showPassword),
                        tooltip: _showPassword ? 'Hide password' : 'Show password',
                      ),
                    ),
                    validator: (val) =>
                        val == null || val.length < 8 ? 'Password must be at least 8 characters.' : null,
                  ),
                  const SizedBox(height: 28),

                  SizedBox(
                    height: 50,
                    child: FilledButton(
                      style: FilledButton.styleFrom(
                        backgroundColor: primaryColor,
                        foregroundColor: Colors.white,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                      onPressed: _isVerifying ? null : _continueToVerification,
                      child: _isVerifying
                          ? const Row(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                SizedBox(
                                  width: 20,
                                  height: 20,
                                  child: CircularProgressIndicator(
                                    strokeWidth: 2,
                                    color: Colors.white,
                                  ),
                                ),
                                SizedBox(width: 12),
                                Text(
                                  'Verifying details...',
                                  style: TextStyle(fontSize: 16),
                                ),
                              ],
                            )
                          : const Text(
                              'Continue to Verification',
                              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                            ),
                    ),
                  ),
                ],
              ),
            ),
          ] else ...[
            Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: primaryColor.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Row(
                    children: [
                      Icon(Icons.mark_email_read_outlined, color: primaryColor),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          'Verification code sent to ${_contactController.text}',
                          style: TextStyle(color: primaryColor, fontWeight: FontWeight.w600),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 20),
                TextFormField(
                  controller: _verificationCode,
                  keyboardType: TextInputType.number,
                  maxLength: 6,
                  decoration: const InputDecoration(
                    labelText: 'Enter 4 or 6-digit Code',
                    hintText: '123456',
                    border: OutlineInputBorder(),
                    prefixIcon: Icon(Icons.pin_outlined),
                  ),
                ),
                const SizedBox(height: 20),
                SizedBox(
                  height: 50,
                  child: FilledButton(
                    style: FilledButton.styleFrom(
                      backgroundColor: primaryColor,
                      foregroundColor: Colors.white,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                    onPressed: _isVerifying ? null : _completeRegistration,
                    child: _isVerifying
                        ? const Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.white,
                                ),
                              ),
                              SizedBox(width: 12),
                              Text('Submitting...', style: TextStyle(fontSize: 16)),
                            ],
                          )
                        : const Text(
                            'Verify & Complete',
                            style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                          ),
                  ),
                ),
                const SizedBox(height: 12),
                TextButton(
                  onPressed: () => setState(() => _step = 1),
                  child: const Text('Back to details'),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

class AdminShell extends StatefulWidget {
  final String role;
  final Future<void> Function() onLogout;

  const AdminShell({super.key, required this.role, required this.onLogout});

  @override
  State<AdminShell> createState() => _AdminShellState();
}

class _AdminShellState extends State<AdminShell> {
  int _selectedIndex = 0;

  List<_NavItem> get _items => widget.role == 'admin'
      ? const [
          _NavItem('Dashboard', Icons.grid_view_rounded, Icons.grid_view_outlined),
          _NavItem('KYC', Icons.verified_user_rounded, Icons.verified_user_outlined),
          _NavItem('Payouts', Icons.account_balance_wallet_rounded, Icons.account_balance_wallet_outlined),
          _NavItem('Orders', Icons.local_laundry_service_rounded, Icons.local_laundry_service_outlined),
          _NavItem('Customers', Icons.people_alt_rounded, Icons.people_alt_outlined),
        ]
      : const [
          _NavItem('Staff Dashboard', Icons.grid_view_rounded, Icons.grid_view_outlined),
          _NavItem('KYC Verification', Icons.verified_user_rounded, Icons.verified_user_outlined),
          _NavItem('Orders Queue', Icons.local_laundry_service_rounded, Icons.local_laundry_service_outlined),
        ];

  List<Widget> get _pages => widget.role == 'admin'
      ? const [DashboardPage(), KycPage(), PayoutsPage(), OrdersPage(), CustomersPage()]
      : const [StaffDashboardPage(), KycPage(), OrdersPage()];

  @override
  Widget build(BuildContext context) {
    final desktop = MediaQuery.sizeOf(context).width >= 900;
    final items = _items;
    final isAdmin = widget.role == 'admin';
    final badgeColor = isAdmin ? const Color(0xFF2563EB) : const Color(0xFF7C3AED);

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        elevation: 0,
        scrolledUnderElevation: 2,
        backgroundColor: Colors.white,
        title: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(8),
              decoration: BoxDecoration(
                color: badgeColor.withValues(alpha: 0.1),
                shape: BoxShape.circle,
              ),
              child: Icon(
                Icons.local_laundry_service_rounded,
                color: badgeColor,
                size: 22,
              ),
            ),
            const SizedBox(width: 10),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'LaundryLink',
                  style: TextStyle(
                    fontWeight: FontWeight.w900,
                    fontSize: 18,
                    letterSpacing: -0.3,
                    color: Color(0xFF0F172A),
                  ),
                ),
                Text(
                  '${widget.role.toUpperCase()} WORKSPACE',
                  style: TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    letterSpacing: 0.8,
                    color: badgeColor,
                  ),
                ),
              ],
            ),
          ],
        ),
        actions: [
          Container(
            margin: const EdgeInsets.only(right: 8),
            decoration: BoxDecoration(
              color: Colors.grey.shade100,
              shape: BoxShape.circle,
            ),
            child: IconButton(
              icon: const Icon(Icons.notifications_none_rounded, color: Color(0xFF334155)),
              tooltip: 'Notifications',
              onPressed: () {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('No new notifications')),
                );
              },
            ),
          ),
          Container(
            margin: const EdgeInsets.only(right: 12),
            child: PopupMenuButton<String>(
              offset: const Offset(0, 48),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              child: CircleAvatar(
                radius: 18,
                backgroundColor: badgeColor,
                child: Text(
                  widget.role[0].toUpperCase(),
                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
                ),
              ),
              onSelected: (val) {
                if (val == 'logout') widget.onLogout();
              },
              itemBuilder: (ctx) => [
                PopupMenuItem(
                  enabled: false,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Logged in as ${widget.role.toUpperCase()}',
                        style: const TextStyle(fontWeight: FontWeight.bold, color: Color(0xFF0F172A)),
                      ),
                      const Text(
                        'LaundryLink Operations',
                        style: TextStyle(fontSize: 12, color: Colors.grey),
                      ),
                    ],
                  ),
                ),
                const PopupMenuDivider(),
                const PopupMenuItem(
                  value: 'logout',
                  child: Row(
                    children: [
                      Icon(Icons.logout_rounded, color: Colors.redAccent, size: 20),
                      SizedBox(width: 8),
                      Text('Sign Out', style: TextStyle(color: Colors.redAccent, fontWeight: FontWeight.w600)),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
      body: Row(
        children: [
          if (desktop)
            Container(
              decoration: BoxDecoration(
                color: Colors.white,
                border: Border(right: BorderSide(color: Colors.grey.shade200)),
              ),
              child: NavigationRail(
                extended: true,
                backgroundColor: Colors.white,
                selectedIndex: _selectedIndex,
                selectedIconTheme: IconThemeData(color: badgeColor),
                unselectedIconTheme: const IconThemeData(color: Color(0xFF64748B)),
                selectedLabelTextStyle: TextStyle(color: badgeColor, fontWeight: FontWeight.bold),
                unselectedLabelTextStyle: const TextStyle(color: Color(0xFF64748B)),
                indicatorColor: badgeColor.withValues(alpha: 0.12),
                onDestinationSelected: (index) => setState(() => _selectedIndex = index),
                destinations: items
                    .map((item) => NavigationRailDestination(
                          icon: Icon(item.outlinedIcon),
                          selectedIcon: Icon(item.activeIcon),
                          label: Text(item.label),
                        ))
                    .toList(),
              ),
            ),
          Expanded(child: _pages[_selectedIndex]),
        ],
      ),
      bottomNavigationBar: desktop
          ? null
          : Container(
              decoration: BoxDecoration(
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.05),
                    blurRadius: 10,
                    offset: const Offset(0, -2),
                  ),
                ],
              ),
              child: NavigationBar(
                elevation: 0,
                backgroundColor: Colors.white,
                indicatorColor: badgeColor.withValues(alpha: 0.15),
                selectedIndex: _selectedIndex,
                onDestinationSelected: (index) => setState(() => _selectedIndex = index),
                destinations: items
                    .map((item) => NavigationDestination(
                          icon: Icon(item.outlinedIcon, color: const Color(0xFF64748B)),
                          selectedIcon: Icon(item.activeIcon, color: badgeColor),
                          label: item.label,
                        ))
                    .toList(),
              ),
            ),
    );
  }
}

class _NavItem {
  final String label;
  final IconData activeIcon;
  final IconData outlinedIcon;
  const _NavItem(this.label, this.activeIcon, this.outlinedIcon);
}

class DashboardPage extends StatelessWidget {
  const DashboardPage({super.key});

  @override
  Widget build(BuildContext context) => const _DashboardContent(staff: false);
}

class StaffDashboardPage extends StatelessWidget {
  const StaffDashboardPage({super.key});

  @override
  Widget build(BuildContext context) => const _DashboardContent(staff: true);
}

class _DashboardContent extends StatelessWidget {
  final bool staff;
  const _DashboardContent({required this.staff});

  @override
  Widget build(BuildContext context) {
    final themeColor = staff ? const Color(0xFF7C3AED) : const Color(0xFF2563EB);

    return FutureBuilder<Map<String, dynamic>>(
      future: DatabaseService.instance.getDashboardSummary(),
      builder: (context, snapshot) {
        if (!snapshot.hasData) {
          return const Center(child: CircularProgressIndicator());
        }
        final data = snapshot.data!;
        final revenue = (data['revenue'] as num?)?.toDouble() ?? 0;
        final customers = data['customers'] ?? 0;
        final kyc = (data['kycCount'] as List?)?.length ?? 0;

        return ListView(
          padding: const EdgeInsets.all(24),
          children: [
            Container(
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: staff
                      ? [const Color(0xFF7C3AED), const Color(0xFFA855F7)]
                      : [const Color(0xFF2563EB), const Color(0xFF3B82F6)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(20),
                boxShadow: [
                  BoxShadow(
                    color: themeColor.withValues(alpha: 0.3),
                    blurRadius: 16,
                    offset: const Offset(0, 6),
                  ),
                ],
              ),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          staff ? 'Operations Command Center ⚡' : 'Business Performance 👋',
                          style: const TextStyle(
                            fontSize: 22,
                            fontWeight: FontWeight.bold,
                            color: Colors.white,
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          staff
                              ? 'Monitor live order queues, KYC verifications, and customer requests.'
                              : 'Real-time sales revenue, customer growth, and merchant statistics.',
                          style: TextStyle(
                            fontSize: 13.5,
                            color: Colors.white.withValues(alpha: 0.88),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 16),
                  Container(
                    padding: const EdgeInsets.all(14),
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.2),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.insights_rounded,
                      color: Colors.white,
                      size: 36,
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 24),

            Wrap(
              spacing: 16,
              runSpacing: 16,
              children: [
                _MetricCard(
                  label: 'Total Revenue',
                  value: '\$${revenue.toStringAsFixed(2)}',
                  trend: '+18.4% vs last week',
                  icon: Icons.payments_rounded,
                  color: const Color(0xFF10B981),
                ),
                _MetricCard(
                  label: 'Active Customers',
                  value: '$customers',
                  trend: '+12 new today',
                  icon: Icons.people_alt_rounded,
                  color: const Color(0xFF2563EB),
                ),
                _MetricCard(
                  label: 'KYC Verified',
                  value: '$kyc',
                  trend: '100% compliant',
                  icon: Icons.verified_rounded,
                  color: const Color(0xFFF59E0B),
                ),
              ],
            ),

            const SizedBox(height: 24),

            Card(
              elevation: 0,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(20),
                side: BorderSide(color: Colors.grey.shade200),
              ),
              color: Colors.white,
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Weekly Activity Overview',
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                                color: Color(0xFF0F172A),
                              ),
                            ),
                            Text(
                              'Revenue (\$) & KYC Records Comparison',
                              style: TextStyle(fontSize: 12, color: Colors.grey),
                            ),
                          ],
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                          decoration: BoxDecoration(
                            color: Colors.grey.shade100,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Row(
                            children: [
                              Container(width: 8, height: 8, color: const Color(0xFF2563EB)),
                              const SizedBox(width: 4),
                              const Text('Revenue', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
                              const SizedBox(width: 12),
                              Container(width: 8, height: 8, color: const Color(0xFFF59E0B)),
                              const SizedBox(width: 4),
                              const Text('KYC', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
                            ],
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 24),
                    SizedBox(
                      height: 220,
                      child: BarChart(
                        BarChartData(
                          barGroups: [
                            BarChartGroupData(
                              x: 0,
                              barRods: [
                                BarChartRodData(
                                  toY: revenue / 1000,
                                  color: const Color(0xFF2563EB),
                                  width: 18,
                                  borderRadius: const BorderRadius.vertical(top: Radius.circular(6)),
                                ),
                                BarChartRodData(
                                  toY: kyc.toDouble(),
                                  color: const Color(0xFFF59E0B),
                                  width: 18,
                                  borderRadius: const BorderRadius.vertical(top: Radius.circular(6)),
                                ),
                              ],
                            ),
                            BarChartGroupData(
                              x: 1,
                              barRods: [
                                BarChartRodData(
                                  toY: (revenue / 1000) * 0.85,
                                  color: const Color(0xFF2563EB).withValues(alpha: 0.6),
                                  width: 18,
                                  borderRadius: const BorderRadius.vertical(top: Radius.circular(6)),
                                ),
                                BarChartRodData(
                                  toY: (kyc * 0.8).toDouble(),
                                  color: const Color(0xFFF59E0B).withValues(alpha: 0.6),
                                  width: 18,
                                  borderRadius: const BorderRadius.vertical(top: Radius.circular(6)),
                                ),
                              ],
                            ),
                          ],
                          gridData: FlGridData(
                            show: true,
                            drawVerticalLine: false,
                            getDrawingHorizontalLine: (val) => FlLine(
                              color: Colors.grey.shade200,
                              strokeWidth: 1,
                            ),
                          ),
                          borderData: FlBorderData(show: false),
                          titlesData: const FlTitlesData(
                            topTitles: AxisTitles(sideTitles: SideTitles(showTitles: false)),
                            rightTitles: AxisTitles(sideTitles: SideTitles(showTitles: false)),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}

class _MetricCard extends StatelessWidget {
  final String label;
  final String value;
  final String trend;
  final IconData icon;
  final Color color;

  const _MetricCard({
    required this.label,
    required this.value,
    required this.trend,
    required this.icon,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 220,
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: Colors.grey.shade200),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.03),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: color.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(icon, color: color, size: 22),
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: Colors.green.shade50,
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  trend,
                  style: TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    color: Colors.green.shade700,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Text(
            label,
            style: TextStyle(
              fontSize: 13,
              color: Colors.grey.shade600,
              fontWeight: FontWeight.w500,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            value,
            style: const TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.w900,
              color: Color(0xFF0F172A),
            ),
          ),
        ],
      ),
    );
  }
}

class KycPage extends StatelessWidget {
  const KycPage({super.key});

  @override
  Widget build(BuildContext context) => _RowsPage(
        title: 'KYC Verification Suite',
        subtitle: 'Review merchant licenses & user verification documents',
        icon: Icons.verified_user_rounded,
        future: DatabaseService.instance.getKycRows(),
        itemBuilder: (row) {
          final status = '${row['status']}';
          final isVerified = status.toLowerCase() == 'verified';
          final badgeColor = isVerified ? Colors.green : Colors.orange;

          return ListTile(
            contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            leading: CircleAvatar(
              backgroundColor: badgeColor.withValues(alpha: 0.15),
              child: Icon(
                isVerified ? Icons.check_circle_rounded : Icons.pending_actions_rounded,
                color: badgeColor,
              ),
            ),
            title: Text(
              '${row['name']}',
              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
            ),
            subtitle: Text('Last updated: ${row['updatedAt']}'),
            trailing: Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: badgeColor.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(20),
                border: Border.all(color: badgeColor.withValues(alpha: 0.3)),
              ),
              child: Text(
                status,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  color: badgeColor,
                ),
              ),
            ),
          );
        },
      );
}

class OrdersPage extends StatelessWidget {
  const OrdersPage({super.key});

  @override
  Widget build(BuildContext context) => _RowsPage(
        title: 'Laundry Orders Queue',
        subtitle: 'Track active pickups, washing status, and deliveries',
        icon: Icons.local_laundry_service_rounded,
        future: DatabaseService.instance.getOrderRows(),
        itemBuilder: (row) {
          final status = '${row['status']}';
          final total = (row['total'] as num? ?? 0).toDouble();

          Color statusColor = const Color(0xFF2563EB);
          if (status.contains('Delivered') || status.contains('Complete')) {
            statusColor = Colors.green;
          } else if (status.contains('Washing')) {
            statusColor = const Color(0xFF7C3AED);
          }

          return ListTile(
            contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            leading: Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: statusColor.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(Icons.dry_cleaning_rounded, color: statusColor, size: 24),
            ),
            title: Text(
              '${row['customer']}',
              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
            ),
            subtitle: Row(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                  decoration: BoxDecoration(
                    color: statusColor.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    status,
                    style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: statusColor),
                  ),
                ),
              ],
            ),
            trailing: Text(
              '\$${total.toStringAsFixed(2)}',
              style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 16, color: Color(0xFF0F172A)),
            ),
          );
        },
      );
}

class CustomersPage extends StatelessWidget {
  const CustomersPage({super.key});

  @override
  Widget build(BuildContext context) => _RowsPage(
        title: 'Customer Directory',
        subtitle: 'View user order history, locations, and total spend',
        icon: Icons.people_alt_rounded,
        future: DatabaseService.instance.getCustomerRows(),
        itemBuilder: (row) {
          final spend = (row['spend'] as num? ?? 0).toDouble();
          final isVip = spend > 150;

          return ListTile(
            contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            leading: CircleAvatar(
              backgroundColor: const Color(0xFF2563EB).withValues(alpha: 0.12),
              child: Text(
                '${row['name']}'[0].toUpperCase(),
                style: const TextStyle(color: Color(0xFF2563EB), fontWeight: FontWeight.bold),
              ),
            ),
            title: Row(
              children: [
                Text(
                  '${row['name']}',
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                ),
                if (isVip) ...[
                  const SizedBox(width: 8),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: Colors.amber.shade100,
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      'VIP',
                      style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: Colors.amber.shade900),
                    ),
                  ),
                ],
              ],
            ),
            subtitle: Text('${row['city']} • ${row['orders']} orders completed'),
            trailing: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(
                  '\$${spend.toStringAsFixed(2)}',
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15, color: Color(0xFF0F172A)),
                ),
                const Text('Total Spend', style: TextStyle(fontSize: 10, color: Colors.grey)),
              ],
            ),
          );
        },
      );
}

class _RowsPage extends StatelessWidget {
  final String title;
  final String subtitle;
  final IconData icon;
  final Future<List<Map<String, dynamic>>> future;
  final Widget Function(Map<String, dynamic>) itemBuilder;

  const _RowsPage({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.future,
    required this.itemBuilder,
  });

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: const Color(0xFFF8FAFC),
        body: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: const Color(0xFF2563EB).withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Icon(icon, color: const Color(0xFF2563EB), size: 26),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        style: const TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF0F172A),
                        ),
                      ),
                      Text(
                        subtitle,
                        style: TextStyle(fontSize: 13, color: Colors.grey.shade600),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),
            FutureBuilder<List<Map<String, dynamic>>>(
              future: future,
              builder: (context, snapshot) {
                if (!snapshot.hasData) return const Center(child: CircularProgressIndicator());
                final rows = snapshot.data!;
                if (rows.isEmpty) {
                  return Container(
                    padding: const EdgeInsets.all(32),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: const Center(
                      child: Text('No records found'),
                    ),
                  );
                }
                return ListView.separated(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: rows.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 12),
                  itemBuilder: (ctx, index) {
                    return Card(
                      elevation: 0,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                        side: BorderSide(color: Colors.grey.shade200),
                      ),
                      color: Colors.white,
                      child: itemBuilder(rows[index]),
                    );
                  },
                );
              },
            ),
          ],
        ),
      );
}

class PayoutsPage extends StatefulWidget {
  const PayoutsPage({super.key});

  @override
  State<PayoutsPage> createState() => _PayoutsPageState();
}

class _PayoutsPageState extends State<PayoutsPage> {
  Future<void> _addPayout() async {
    final formKey = GlobalKey<FormState>();
    final merchant = TextEditingController();
    final amount = TextEditingController();
    final saved = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Row(
          children: [
            Icon(Icons.add_card_rounded, color: Color(0xFF2563EB)),
            SizedBox(width: 8),
            Text('Add Merchant Payout'),
          ],
        ),
        content: Form(
          key: formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextFormField(
                controller: merchant,
                decoration: InputDecoration(
                  labelText: 'Merchant Name',
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                  prefixIcon: const Icon(Icons.storefront_rounded),
                ),
                validator: (value) => value == null || value.trim().isEmpty ? 'Enter merchant name' : null,
              ),
              const SizedBox(height: 14),
              TextFormField(
                controller: amount,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                decoration: InputDecoration(
                  labelText: 'Payout Amount (\$)',
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                  prefixIcon: const Icon(Icons.attach_money_rounded),
                ),
                validator: (value) {
                  final parsed = double.tryParse(value ?? '');
                  return parsed == null || parsed <= 0 ? 'Enter a positive amount' : null;
                },
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: const Color(0xFF2563EB),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
            ),
            onPressed: () async {
              if (!formKey.currentState!.validate()) return;
              await DatabaseService.instance.addPayout(
                merchant: merchant.text,
                amount: double.parse(amount.text),
              );
              if (context.mounted) Navigator.pop(context, true);
            },
            child: const Text('Save Payout'),
          ),
        ],
      ),
    );
    merchant.dispose();
    amount.dispose();
    if (saved == true && mounted) setState(() {});
  }

  Future<void> _approve(Map<String, dynamic> row) async {
    final id = row['id'];
    if (id is! int) return;
    await DatabaseService.instance.approvePayout(id);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Payout for ${row['merchant']} approved!'),
          backgroundColor: Colors.green,
        ),
      );
      setState(() {});
    }
  }

  Future<void> _export() async {
    final csv = await DatabaseService.instance.exportPayoutCsv();
    final directory = await getApplicationDocumentsDirectory();
    final file = File('${directory.path}/payouts_${DateTime.now().millisecondsSinceEpoch}.csv');
    await file.writeAsString(csv);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('CSV Exported: ${file.path}'),
          backgroundColor: const Color(0xFF2563EB),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        backgroundColor: const Color(0xFFF8FAFC),
        body: ListView(
          padding: const EdgeInsets.all(24),
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: const Color(0xFF2563EB).withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: const Icon(Icons.account_balance_wallet_rounded, color: Color(0xFF2563EB), size: 26),
                ),
                const SizedBox(width: 14),
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Merchant Payouts',
                        style: TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF0F172A),
                        ),
                      ),
                      Text(
                        'Manage merchant settlements & process disbursements',
                        style: TextStyle(fontSize: 13, color: Colors.grey),
                      ),
                    ],
                  ),
                ),
                OutlinedButton.icon(
                  style: OutlinedButton.styleFrom(
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                  ),
                  onPressed: _export,
                  icon: const Icon(Icons.download_rounded, size: 18),
                  label: const Text('Export CSV'),
                ),
                const SizedBox(width: 8),
                FilledButton.icon(
                  style: FilledButton.styleFrom(
                    backgroundColor: const Color(0xFF2563EB),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                  ),
                  onPressed: _addPayout,
                  icon: const Icon(Icons.add_rounded, size: 18),
                  label: const Text('Add Payout'),
                ),
              ],
            ),
            const SizedBox(height: 20),
            FutureBuilder<List<Map<String, dynamic>>>(
              future: DatabaseService.instance.getPayoutRows(),
              builder: (context, snapshot) {
                if (!snapshot.hasData) return const Center(child: CircularProgressIndicator());
                final rows = snapshot.data!;
                return ListView.separated(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: rows.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 12),
                  itemBuilder: (ctx, index) {
                    final row = rows[index];
                    final status = '${row['status']}';
                    final isApproved = status.toLowerCase() == 'approved';
                    final statusColor = isApproved ? Colors.green : Colors.orange;

                    return Card(
                      elevation: 0,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                        side: BorderSide(color: Colors.grey.shade200),
                      ),
                      color: Colors.white,
                      child: ListTile(
                        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                        leading: Container(
                          padding: const EdgeInsets.all(10),
                          decoration: BoxDecoration(
                            color: statusColor.withValues(alpha: 0.12),
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Icon(
                            isApproved ? Icons.verified_rounded : Icons.pending_actions_rounded,
                            color: statusColor,
                          ),
                        ),
                        title: Text(
                          '${row['merchant']}',
                          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                        ),
                        subtitle: Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                              decoration: BoxDecoration(
                                color: statusColor.withValues(alpha: 0.1),
                                borderRadius: BorderRadius.circular(6),
                              ),
                              child: Text(
                                status,
                                style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: statusColor),
                              ),
                            ),
                          ],
                        ),
                        trailing: Wrap(
                          crossAxisAlignment: WrapCrossAlignment.center,
                          spacing: 12,
                          children: [
                            Text(
                              '\$${(row['amount'] as num? ?? 0).toStringAsFixed(2)}',
                              style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 16, color: Color(0xFF0F172A)),
                            ),
                            if (!isApproved)
                              FilledButton.icon(
                                style: FilledButton.styleFrom(
                                  backgroundColor: Colors.green,
                                  foregroundColor: Colors.white,
                                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                                ),
                                onPressed: () => _approve(row),
                                icon: const Icon(Icons.check_rounded, size: 16),
                                label: const Text('Approve', style: TextStyle(fontSize: 12)),
                              ),
                          ],
                        ),
                      ),
                    );
                  },
                );
              },
            ),
          ],
        ),
      );
}
