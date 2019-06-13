#!/usr/bin/env bash
cd $HOME/chloe && mkdir -p logs && (git checkout --quiet master >/dev/null && git fetch -p --quiet >/dev/null && git pull --quiet >/dev/null) && (sudo mvn clean compile exec:exec@MasterNodeApplication -P prod 2> >(sudo tee $HOME/chloe/logs/err.log >/dev/null &) >/dev/null &) && echo master node response: started @`curl -s http://checkip.amazonaws.com/`
exit 1
