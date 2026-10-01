# Customer app setup

Add a `MAPS_API_KEY` entry to `local.properties` and replace
`YOUR_GOOGLE_MAPS_API_KEY` with a Google Maps SDK for Android key. You can use
`local.properties.example` as a template; preserve any existing `sdk.dir`
entry. The local properties file is ignored by Git. Alternatively, set the
`MAPS_API_KEY` environment variable when building.

Restrict the key to the Android Maps SDK and this app's package name and signing
certificate before using it outside local development.
