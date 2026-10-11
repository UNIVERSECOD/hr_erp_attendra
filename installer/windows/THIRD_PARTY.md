# Bundled components

Attendra's Windows package includes unmodified PostgreSQL 15 binaries from EnterpriseDB (PostgreSQL license; see postgres/server_license.txt and third-party notices), a Java 21 runtime produced from Eclipse Temurin (GPLv2 with Classpath Exception; see java/legal), Caddy (Apache-2.0; Caddy-LICENSE), and Windows Service Wrapper 2.12 (MIT; WinSW-LICENSE.txt).

NSIS builds the installer and uses the zlib/libpng license for its installer stub. Source and license: https://nsis.sourceforge.io/License . It is not installed as a customer prerequisite.

Microsoft Visual C++ x64 Redistributable is included in its original signed installer form and is governed by Microsoft's included license terms. Official download: https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist/ .

Upstream source/download locations:
- https://www.postgresql.org/download/windows/
- https://www.enterprisedb.com/download-postgresql-binaries
- https://github.com/adoptium/temurin21-binaries
- https://github.com/caddyserver/caddy
- https://github.com/winsw/winsw
- https://sourceforge.net/projects/nsis/

The generated files.json lists SHA256 hashes for installed artifacts. No customer database, environment file, device credential or employee photo is part of the payload.
