// the studio is only ever reached through the gateway (:1010), which signs you in and
// routes /api to the api and everything else here, as it did for the Vue client's vite
// server. so: the same port vite used, every unknown path answered with index.html for
// the client-side routes, and live reload talking to this server directly, since the
// gateway (spring cloud gateway server *mvc*) cannot proxy websockets.
if (config.devServer) {
    config.devServer.port = 5173
    config.devServer.host = '127.0.0.1'
    config.devServer.historyApiFallback = true
    config.devServer.allowedHosts = 'all'
    config.devServer.client = { ...(config.devServer.client || {}), webSocketURL: 'ws://127.0.0.1:5173/ws' }
}
