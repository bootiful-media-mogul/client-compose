package com.joshlong.mogul.client.app

/**
 * where the apps find mogul and auth0. the values come from gradle.properties or
 * local.properties, by way of the generateAppConfig task in shared/build.gradle.kts
 */
internal data class AppConfig(
    /** the gateway, as this machine sees it */
    val server: String,
    /** https://<tenant>.auth0.com/ */
    val auth0Domain: String,
    /** the auth0 application the apps sign in with: a native one, so no secret */
    val auth0ClientId: String,
    /** what the api (and the gateway) check tokens are for */
    val auth0Audience: String,
    /** where auth0 sends the phone and tablet apps back to */
    val redirectUri: String,
    /** where auth0 sends the desktop app back to: a port it listens on */
    val desktopRedirectUri: String,
)
