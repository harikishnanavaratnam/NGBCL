#!/usr/bin/env bash
# One-time setup for a fresh Ubuntu 24.04 server (Oracle Cloud Ampere A1). Run as the default user with sudo:
#   bash deploy/scripts/server-setup.sh
set -Eeuo pipefail

sudo apt-get update
sudo DEBIAN_FRONTEND=noninteractive apt-get -y upgrade
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y docker.io docker-compose-v2 git curl unattended-upgrades fail2ban iptables-persistent
sudo usermod -aG docker "$USER"
sudo systemctl enable --now docker fail2ban
sudo dpkg-reconfigure -f noninteractive unattended-upgrades

# Oracle's Ubuntu images reject everything except SSH in iptables (on top of the VCN security list).
# Open HTTP/HTTPS just before the final REJECT rule and keep it across reboots.
for port in 80 443; do
  sudo iptables -C INPUT -p tcp --dport "$port" -m state --state NEW -j ACCEPT 2>/dev/null \
    || sudo iptables -I INPUT 6 -p tcp --dport "$port" -m state --state NEW -j ACCEPT
done
sudo iptables -C INPUT -p udp --dport 443 -j ACCEPT 2>/dev/null || sudo iptables -I INPUT 6 -p udp --dport 443 -j ACCEPT
sudo netfilter-persistent save

# Container logs are size-limited in the compose file; also cap the systemd journal.
sudo sed -i 's/^#\?SystemMaxUse=.*/SystemMaxUse=500M/' /etc/systemd/journald.conf
sudo systemctl restart systemd-journald

echo
echo "Done. Log out and back in (docker group), then follow deploy/README.md from step 4."
