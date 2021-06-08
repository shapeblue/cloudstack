#!/bin/bash

# 1. create overlayfs layers
mkdir -p /cloudstack/opt/data
mkdir -p /cloudstack/opt/upper
mkdir -p /cloudstack/opt/workdir

mkdir -p /cloudstack/etc/data
mkdir -p /cloudstack/etc/upper
mkdir -p /cloudstack/etc/workdir

mkdir -p /cloudstack/var/data
mkdir -p /cloudstack/var/upper
mkdir -p /cloudstack/var/workdir

mkdir -p /cloudstack/root/data
mkdir -p /cloudstack/root/upper
mkdir -p /cloudstack/root/workdir

mkdir -p /cloudstack/usr/data
mkdir -p /cloudstack/usr/upper
mkdir -p /cloudstack/usr/workdir

# 2. mount overlayfs
mount -t overlay -o lowerdir=/cloudstack/etc/data:/etc,upperdir=/cloudstack/etc/upper,workdir=/cloudstack/etc/workdir overlay /etc
mount -t overlay -o lowerdir=/cloudstack/opt/data:/opt,upperdir=/cloudstack/opt/upper,workdir=/cloudstack/opt/workdir overlay /opt
mount -t overlay -o lowerdir=/cloudstack/var/data:/var,upperdir=/cloudstack/var/upper,workdir=/cloudstack/var/workdir overlay /var
mount -t overlay -o lowerdir=/cloudstack/usr/data:/usr,upperdir=/cloudstack/usr/upper,workdir=/cloudstack/usr/workdir overlay /usr
mount -t overlay -o lowerdir=/cloudstack/root/data:/root,upperdir=/cloudstack/root/upper,workdir=/cloudstack/root/workdir overlay /root

# 3. upgrade kernel and installed packages
apt update && apt upgrade -y && apt dist-upgrade -y

# 4. install new packages, please append packages name
apt-get -q -y -o DPkg::Options::=--force-confold -o DPkg::Options::=--force-confdef install frr

# 5. cleanup
apt-get -y autoremove --purge
apt-get autoclean
apt-get clean
# Scripts
rm -fr /home/cloud/cloud_scripts*
rm -f /usr/share/cloud/cloud-scripts.tar
rm -f /root/.rnd
rm -f /var/www/html/index.html
# Logs
rm -f /var/log/*.log
rm -f /var/log/apache2/*
rm -f /var/log/messages
rm -f /var/log/syslog
rm -f /var/log/messages
rm -fr /var/log/apt
rm -fr /var/log/installer
# Docs and data files
rm -fr /var/lib/apt/*
rm -fr /var/cache/apt/*
rm -fr /var/cache/debconf/*old
rm -fr /usr/share/doc
rm -fr /usr/share/man
rm -fr /usr/share/info
rm -fr /usr/share/lintian
rm -fr /usr/share/apache2/icons
find /usr/share/locale -type f | grep -v en_US | xargs rm -fr
find /usr/share/zoneinfo -type f | grep -v UTC | xargs rm -fr
rm -fr /tmp/*
