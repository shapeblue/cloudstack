#!/bin/bash

IMAGE=$1
ACTION=$2
OUTPUT=cloudstack-patches-debian-10.tgz
PWD=$(readlink -f ../..)

if [ -z $IMAGE ];then
    echo "please input a image file"
    exit
fi

if [ ! -f $IMAGE ];then
    echo "image file not found: $IMAGE"
    exit
fi

if [[ $IMAGE == *.bz2 ]];then
    echo "Uncompressing $IMAGE"
    IMAGE=${IMAGE%.bz2}
    rm -f $IMAGE
    bzip2 -dk $IMAGE.bz2
fi

if [ "$ACTION" = "inject" ];then
    echo "Injecting cloudstack-mount.service to $IMAGE"
    virt-customize -x --run systemvmtemplate/scripts/configure_overlayfs.sh -a $IMAGE
    echo "Injected cloudstack-mount.service to $IMAGE"
    exit
fi

customize_image() {
    local image=$1
    echo "Customizing $image"
    virt-customize -x \
      --upload $PWD/systemvm/debian/etc/systemd/system/cloud-early-config.service:/tmp \
      --upload $PWD/systemvm/debian/opt/cloud/bin/setup/cloud-early-config:/tmp \
      --upload $PWD/systemvm/debian/etc/systemd/system/cloud-postinit.service:/tmp \
      --upload $PWD/systemvm/debian/opt/cloud/bin/setup/postinit.sh:/tmp \
      --run cloudstack_install_packages.sh -a $image
}

# refer to https://github.com/alpinelinux/alpine-make-vm-image/blob/master/alpine-make-vm-image
get_available_nbd() {
	local dev; for dev in $(find /dev -maxdepth 2 -name 'nbd[0-9]*'); do
		if [ "$(blockdev --getsize64 "$dev")" -eq 0 ]; then
			echo "$dev"; return 0
		fi
	done
	return 1
}

# Attaches the specified image as a NBD block device and prints its path.
attach_image() {
	local image="$1"
	local format="${2:-}"
	local nbd_dev

	nbd_dev=$(get_available_nbd) || {
		modprobe nbd max_part=0
		sleep 1
		nbd_dev=$(get_available_nbd)
	} || die 'No available nbd device found!'

	qemu-nbd --connect="$nbd_dev" --cache=writeback \
		${format:+--format=$format} "$image" \
		&& echo "$nbd_dev"
}

disconnect_image() {
    local nbd_dev=$1
    qemu-nbd --disconnect "$nbd_dev"
}

compress_nbd() {
    local nbd_dev=$1
    randomstr=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1)
    tmp=/tmp/nbd.$randomstr

    echo "Mounting to $tmp"
    mkdir $tmp
    sleep 1
    mount ${nbd_dev}p5 $tmp

    dirname=$(realpath -s .)
    cd $tmp/cloudstack/

    find . -maxdepth 2 -name upper -print0 | xargs -0 tar cfvz $dirname/$OUTPUT --transform 's,^./\(etc\|usr\|var\|opt\|root\)/upper,./\1/data,'
    sha512sum $dirname/$OUTPUT | awk '{print $1}' > $dirname/$OUTPUT.sha512sum

    cd $dirname
    umount $tmp
    rm -rf $tmp
}

cleanup() {
    rm -f $IMAGE
}

#customize_image $IMAGE
nbd_dev=$(attach_image $IMAGE qcow2)
compress_nbd $nbd_dev
disconnect_image $nbd_dev
#cleanup
