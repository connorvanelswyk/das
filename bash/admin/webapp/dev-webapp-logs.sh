#!/usr/bin/env bash

ssh -i $HOME/eb ec2-user@34.198.172.77 -o StrictHostKeyChecking=no ServerAliveInterval=120 ClientAliveInterval=120 'tail -f -n 512 /var/log/tomcat8/catalina.out'
