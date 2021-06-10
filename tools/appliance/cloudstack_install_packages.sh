#!/bin/sh

CLOUDSTACK_RELEASE=4.16.0.0

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

export DEBIAN_FRONTEND=noninteractive
arch=`dpkg --print-architecture`

  echo "strongwan strongwan/install_x509_certificate boolean false" | debconf-set-selections
  echo "strongwan strongwan/install_x509_certificate seen true" | debconf-set-selections
  echo "iptables-persistent iptables-persistent/autosave_v4 boolean true" | debconf-set-selections
  echo "iptables-persistent iptables-persistent/autosave_v6 boolean true" | debconf-set-selections

# 3. upgrade kernel and installed packages
apt update && apt upgrade -y && apt dist-upgrade -y && apt-get -q -y upgrade -t buster-backports && apt-get -q -y dist-upgrade -t buster-backports

# 4. install new packages, please append packages name
apt-get -q -y -o dpkg::options::=--force-confold -o dpkg::options::=--force-confdef install \
    rsyslog logrotate cron net-tools ifupdown tmux vim-tiny htop netbase iptables nftables \
    openssh-server e2fsprogs tcpdump iftop socat wget coreutils systemd \
    python python3 bzip2 sed gawk diffutils grep gzip less tar telnet ftp rsync traceroute psmisc lsof procps \
    inetutils-ping iputils-arping httping curl \
    dnsutils zip unzip ethtool uuid file iproute2 acpid sudo \
    sysstat python-netaddr \
    apache2 ssl-cert \
    dnsmasq dnsmasq-utils \
    nfs-common \
    samba-common cifs-utils \
    xl2tpd bcrelay ppp tdb-tools \
    xenstore-utils libxenstore3.0 \
    ipvsadm conntrackd libnetfilter-conntrack3 \
    keepalived irqbalance \
    ipcalc \
    openjdk-11-jre-headless \
    ipset \
    iptables-persistent \
    libtcnative-1 libssl-dev libapr1-dev \
    python-flask \
    haproxy \
    haveged \
    radvd \
    sharutils genisoimage \
    strongswan libcharon-extra-plugins libstrongswan-extra-plugins strongswan-charon strongswan-starter \
    virt-what open-vm-tools qemu-guest-agent hyperv-daemons

  apt-get -q -y install links

  #32 bit architecture support for vhd-util: not required for 32 bit template
  if [ "${arch}" != "i386" ]; then
    dpkg --add-architecture i386
    apt-get update
    apt-get -q -y libuuid1:i386 libc6:i386
  fi

  wget --no-check-certificate https://github.com/shapeblue/cloudstack-nonoss/raw/master/vhd-util -O /bin/vhd-util
  chmod a+x /bin/vhd-util

  # Install xenserver guest utilities as debian repos don't have it
  wget https://mirrors.kernel.org/ubuntu/pool/main/x/xe-guest-utilities/xe-guest-utilities_7.10.0-0ubuntu1_amd64.deb
  dpkg -i xe-guest-utilities_7.10.0-0ubuntu1_amd64.deb
  rm -f xe-guest-utilities_7.10.0-0ubuntu1_amd64.deb

# 5. configure
mkdir -p /opt/cloud/bin/setup/
cp /tmp/cloud-postinit.service /etc/systemd/system/cloud-postinit.service
cp /tmp/postinit.sh /opt/cloud/bin/setup/postinit.sh
cp /tmp/cloud-early-config.service /etc/systemd/system/cloud-early-config.service
cp /tmp/cloud-early-config /opt/cloud/bin/setup/cloud-early-config
systemctl daemon-reload
systemctl enable cloud-early-config
systemctl enable cloud-postinit

mkdir -p /var/cache/cloud/ /usr/share/cloud/
echo "Cloudstack Release $CLOUDSTACK_RELEASE $(date)" > /etc/cloudstack-release

  systemctl disable apache2
  systemctl disable conntrackd
  systemctl disable console-setup
  systemctl disable dnsmasq
  systemctl disable haproxy
  systemctl disable keepalived
  systemctl disable radvd
  systemctl disable strongswan
  systemctl disable x11-common
  systemctl disable xl2tpd
  systemctl disable vgauth
  systemctl disable sshd
  systemctl disable nfs-common
  systemctl disable portmap

  # Disable guest services which will selectively be started based on hypervisor
  systemctl disable open-vm-tools
  systemctl disable xe-daemon
  systemctl disable hyperv-daemons.hv-fcopy-daemon.service
  systemctl disable hyperv-daemons.hv-kvp-daemon.service
  systemctl disable hyperv-daemons.hv-vss-daemon.service
  systemctl disable qemu-guest-agent

configure_apache2() {
   # Enable ssl, rewrite and auth
   a2enmod ssl rewrite auth_basic auth_digest
   a2ensite default-ssl
   # Backup stock apache configuration since we may modify it in Secondary Storage VM
   cp /etc/apache2/sites-available/000-default.conf /etc/apache2/sites-available/default.orig
   cp /etc/apache2/sites-available/default-ssl.conf /etc/apache2/sites-available/default-ssl.orig
   sed -i 's/SSLProtocol .*$/SSLProtocol TLSv1.2/g' /etc/apache2/mods-available/ssl.conf
}

configure_strongswan() {
  # change the charon stroke timeout from 3 minutes to 30 seconds
  sed -i "s/# timeout = 0/timeout = 30000/" /etc/strongswan.d/charon/stroke.conf
}
configure_issue() {
  cat > /etc/issue <<EOF

   __?.o/  Apache CloudStack SystemVM $CLOUDSTACK_RELEASE
  (  )#    https://cloudstack.apache.org
 (___(_)   Debian GNU/Linux 10 \n \l

EOF
}

configure_cacerts() {
  CDIR=$(pwd)
  cd /tmp
  # Add LetsEncrypt ca-cert
  wget https://letsencrypt.org/certs/lets-encrypt-x3-cross-signed.der
  keytool -trustcacerts -keystore /etc/ssl/certs/java/cacerts -storepass changeit -noprompt -importcert -alias letsencryptauthorityx3cross -file lets-encrypt-x3-cross-signed.der
  rm -f lets-encrypt-x3-cross-signed.der
  cd $CDIR
}

  configure_apache2
  configure_strongswan
  configure_issue
  configure_cacerts

disable_conntrack_logging() {
  grep "LogFile off" /etc/conntrackd/conntrackd.conf && return

  sed -i '/Stats {/,/}/ s/LogFile on/LogFile off/' /etc/conntrackd/conntrackd.conf
  rm -f /var/log/conntrackd-stats.log
}

disable_conntrack_logging

# 6. cleanup
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
