#!/bin/bash

hostnamectl set-hostname systemvm
echo 'systemvm' > /etc/hostname
sed -i 's/debian/systemvm/g' /etc/hosts
