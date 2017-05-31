#!/usr/bin/env bash
mkdir resources/public/assets/css
echo "Installing NPM dependencies..."
npm install
echo "Compiling SASS..."
sass -E "UTF-8" style/main.sass:resources/public/assets/css/apply.css --style compressed
echo "Compiling Clojure & ClojureScript..."
lein uberjar
