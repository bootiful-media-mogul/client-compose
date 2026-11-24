package com.joshlong.mogul.client.app

import com.joshlong.mogul.client.api.Mogul
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.publicvalue.multiplatform.oidc.ExperimentalOpenIdConnect
import org.publicvalue.multiplatform.oidc.OpenIdConnectClient
import org.publicvalue.multiplatform.oidc.ktor.clearTokens
import org.publicvalue.multiplatform.oidc.tokenstore.TokenRefreshHandler
import org.publicvalue.multiplatform.oidc.tokenstore.TokenStore
import org.publicvalue.multiplatform.oidc.types.CodeChallengeMethod

/**
 * the apps are oauth clients of their own, as Mogul Clip is: they sign in to auth0 in the system
 * browser with pkce, keep their tokens on the device, and send the access token to the gateway,
 * which checks it and passes it on to the api
 */
@OptIn(ExperimentalOpenIdConnect::class)
internal object AppAuth {

    private val config = appConfig

    /** the gateway, as this device reaches it */
    val server: String = NativePlatform.server(this.config.server).trimEnd('/')

    val tokens: TokenStore = NativePlatform.tokenStore

    val oidc: OpenIdConnectClient = OpenIdConnectClient(discoveryUri = this.config.auth0Domain + ".well-known/openid-configuration") {
        clientId = this@AppAuth.config.auth0ClientId
        // openid profile email: the api looks the mogul up at auth0's /userinfo with this token
        // the first time it sees them. offline_access: a refresh token, so a session outlives an hour
        scope = "openid profile email offline_access"
        codeChallengeMethod = CodeChallengeMethod.S256
        redirectUri = NativePlatform.redirectUri
    }

    val refreshHandler = TokenRefreshHandler(this.tokens)

    /**
     * the access token as of now, for the few requests that don't go through the HTTP client:
     * the audio players stream the api's files themselves, and need it as a header
     */
    var accessToken: String? = null
        private set

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            this@AppAuth.tokens.accessTokenFlow.collect { this@AppAuth.accessToken = it }
        }
    }

    suspend fun signIn() {
        if (this.oidc.discoverDocument == null) this.oidc.discover()
        // auth0 only issues a token the api can verify when it's asked for the audience. prompt=login:
        // the browser may still have an auth0 session, and without it auth0 would quietly reuse that
        // session's provider instead of letting the user pick another
        val response = NativePlatform.authFlows.createAuthFlow(this.oidc)
            .getAccessToken(configureAuthUrl = {
                parameters.append("audience", this@AppAuth.config.auth0Audience)
                parameters.append("prompt", "login")
            })
        this.tokens.saveTokens(response)
        // the bearer plugin remembers the tokens it last loaded, which until now were none
        Mogul.http.clearTokens()
    }

    suspend fun signOut() {
        this.tokens.removeTokens()
        Mogul.http.clearTokens()
        // the session auth0 keeps in the system browser outlives the tokens: end it there too
        NativePlatform.openUrl(this.config.auth0Domain + "v2/logout")
    }
}
