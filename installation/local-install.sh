#!/usr/bin/env bash
echo stopping
sudo systemctl stop bitplay2
while true; do
    if [ $(systemctl is-active bitplay2) == "failed" ]; then
        break
    fi

    sleep 1
done
echo stopped


sudo cp ../core/target/bitplay-0.0.1-SNAPSHOT.jar /opt/bitplay/bitmex-okcoin/bitplay.jar
echo copied

echo starting
sudo systemctl start bitplay2
while true; do
    if [ $(systemctl is-active bitplay2) == "active" ]; then
        break
    fi

    sleep 1
done
echo started

