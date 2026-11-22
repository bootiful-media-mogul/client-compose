// okio (pulled in by Coil) references Node's os and path on a code path that never runs in the browser
config.resolve = config.resolve || {};
config.resolve.fallback = Object.assign({}, config.resolve.fallback, { os: false, path: false });
