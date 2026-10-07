#!/bin/sh
set -eu
cd "$(dirname "$0")"
if ! id audioly >/dev/null 2>&1; then
    useradd --system --no-create-home --shell /usr/sbin/nologin audioly
fi
install -d -m 755 /opt/audioly
if [ -f /opt/audioly/audioly-party ]; then
    cp -p /opt/audioly/audioly-party /opt/audioly/audioly-party.previous
fi
install -m 755 audioly-party /opt/audioly/audioly-party.new
mv /opt/audioly/audioly-party.new /opt/audioly/audioly-party
install -m 644 audioly-party.service /etc/systemd/system/audioly-party.service
caddy validate --config Caddyfile --adapter caddyfile
if [ -f /etc/caddy/Caddyfile ]; then
    cp -p /etc/caddy/Caddyfile /etc/caddy/Caddyfile.before-audioly
fi
install -m 644 Caddyfile /etc/caddy/Caddyfile
systemctl daemon-reload
systemctl enable --now audioly-party
systemctl restart audioly-party
systemctl enable --now caddy
systemctl reload caddy
sleep 2
curl --fail --silent http://127.0.0.1:8000/healthz
systemctl --no-pager --full status audioly-party
