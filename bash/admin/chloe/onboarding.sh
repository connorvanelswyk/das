#!/usr/bin/env bash
# sudo apt-get install npm nodejs nodejs-legacy libxml2-dev libxslt1-dev python-dev libhyphen-dev; sudo npm -g install phantomjs-prebuilt

sudo apt-get update && yes | sudo apt-get install git maven && sudo timedatectl set-timezone America/New_York && ssh-keygen -t rsa -N "" -f ~/.ssh/id_rsa;


# java java java
wget -c --header "Cookie: oraclelicense=accept-securebackup-cookie" http://download.oracle.com/otn-pub/java/jdk/9.0.4+11/c2514751926b4512b076cc82f959763f/jdk-9.0.4_linux-x64_bin.tar.gz &&
 sudo mkdir -p /opt/jdk && sudo tar xvzf jdk-9.0.4_linux-x64_bin.tar.gz -C /opt/jdk/ && sudo rm jdk-9.0.4_linux-x64_bin.tar.gz &&
 sudo rm /usr/bin/java; sudo rm /usr/bin/javac; sudo ln -s /opt/jdk/jdk-9.0.4/bin/java /usr/bin/java && sudo ln -s /opt/jdk/jdk-9.0.4/bin/javac /usr/bin/javac

# create swap space
sudo dd if=/dev/zero of=/var/swapfile bs=1M count=2048 && sudo chmod 600 /var/swapfile && sudo mkswap /var/swapfile && echo /var/swapfile none swap defaults 0 0 | sudo tee -a /etc/fstab && sudo swapon -a


ssh-keygen; cat ~/.ssh/id_rsa.pub;
# copy ssh key to bitbucket access keys (under settings) then
ssh-keyscan bitbucket.org >>~/.ssh/known_hosts && git clone git@bitbucket.org:plainview_team_id/chloe.git && sudo chmod -R +x ~/chloe/bash/



# if jdk 8 was previously installed...
# yes | sudo apt-get remove openjdk-8*


sudo apt-get -y update && sudo apt-get -y upgrade && sudo apt-get -y dist-upgrade && sudo apt -y autoremove


# launch the startup script on reboot via crontab
#crontab -e -u ubuntu
# add this line
#@reboot /home/ubuntu/gpx/bash/start-worker.sh


# le startup script for new nodes
#!/bin/sh

sudo apt-get -y update && sudo apt-get -y upgrade && sudo apt-get -y dist-upgrade && sudo apt -y autoremove; sudo apt-get -y install git maven; sudo timedatectl set-timezone America/New_York; wget -c --header "Cookie: oraclelicense=accept-securebackup-cookie" http://download.oracle.com/otn-pub/java/jdk/9.0.4+11/c2514751926b4512b076cc82f959763f/jdk-9.0.4_linux-x64_bin.tar.gz; sudo mkdir /opt/jdk && sudo tar xvzf jdk-9.0.4_linux-x64_bin.tar.gz -C /opt/jdk/ && sudo rm jdk-9.0.4_linux-x64_bin.tar.gz; sudo rm /usr/bin/java; sudo rm /usr/bin/javac; sudo ln -s /opt/jdk/jdk-9.0.4/bin/java /usr/bin/java; sudo ln -s /opt/jdk/jdk-9.0.4/bin/javac /usr/bin/javac; ssh-keygen -t rsa -N "" -f ~/.ssh/id_rsa; sudo dd if=/dev/zero of=/var/swapfile bs=1M count=2048 && sudo chmod 600 /var/swapfile && sudo mkswap /var/swapfile && echo /var/swapfile none swap defaults 0 0 | sudo tee -a /etc/fstab && sudo swapon -a;


# add ze repo
ssh-keyscan bitbucket.org >> ~/.ssh/known_hosts
git clone git@bitbucket.org:plainview_team_id/chloe.git



curl -s http://checkip.amazonaws.com/

# elastic beanstalk connection
ssh -i keys/eb.pem ec2-user@ec2-35-169-109-3.compute-1.amazonaws.com 'tail -f /var/log/tomcat8/catalina.out'

# find java location (from /)
sudo find . -print | grep -i java

# regex replace blank lines
^([ \t]*)\r?\n
