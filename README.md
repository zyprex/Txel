# Txel

The name "Txel" stands for "Transport (tx) Every Location (el)".
It's use WiFi network to transfer things to any other devices.

# Quick View

1. transmit plain text
2. transmit a file
3. transmit a directory
4. upload a file
5. generate a qrcode

# F&Q

## Before start

Make sure all your devices have connect to the same wifi.




## How to transmit plain text

1. open link (The link show in app can be click)
2. choose "Link" button
3. input something on text input area
4. click "submit"
5. get text in other browser by just refresh webpage or click "refresh"

Get raw text: `http://localhost:8080/r`

Prepare the text: `http://localhost:8080/t?you see me!`

## About transmit a file or a directory

- This app use SAF to choose file and directory.
- When you back to previous webpage in app's file list page, it require refresh page again.
- When a directory in your choose directory, it normally can't be access except you has chose it before.
- When you restart phone or reinstalled app, you will lost access permission for you recent chose directory.

You can share some file to this app (didn't support directory or type unknown files), then it will auto fill file's path.

## Upload file

The files been uploaded will always save to phone's Download directory.

## About QRCode generate

In home page enter "QRCode", make sure then parameters you enter is correct, or else it will malfunction.

See "[QRCode-kotlin]()"

## Configure

There are some options you can set.

1. Dark Mode: dark light mode switch
2. Port: set the default port
3. Mine Type: customize your own mine-type (will take precedence).

## If network unavailable ?

It will connect to `localhost` if your phone didn't have a network connection.

## Port in use ?

If you see a prompt tell you that port in use, that's mean the http server exit due to socket bind error.
Others is using this port. But Service not quit, by it you can see whose grab your port.

## Connect reset ?

Restart server


# Credit

- [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd)
- [QRCode-kotlin](https://github.com/g0dkar/qrcode-kotlin)


[QRCode-kotlin]: https://qrcodekotlin.com
