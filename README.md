# Txel

The name "Txel" stands for "Transport (tx) Every Location (el)".
It's use WiFi network to transfer things to any other devices.
Require Android version 5.0+.

# Feature Quick View

1. send plain text, convert any "http(s)://..." text to clickable link. 
2. send a file
3. server a directory (current can't recursive to subdir)
4. upload a file
5. unzip a zip file and server as directory
6. generate a qrcode
7. a screen saver for long time connection
8. support resuming and multi-thread download

# F&Q

## Before start

Make sure all your devices are connect to the same wifi or hotspot.

## How to transmit plain text

1. open link (The link show in app can be click)
2. click "Link" button
3. input something on text input area
4. click "submit" button
5. get text in other browser by just refresh webpage or click "refresh" button

Get raw text: `http://localhost:8080/r`

Prepare the text: `http://localhost:8080/t?you see me!`

## About transmit a file or a directory

Txel use SAF to choose file and directory. Here are some limitations:

- When you back to previous webpage in app's file list page, it require refresh page again.
- When a directory in your choose directory, it may can't be access.
- On Android 11 or higher, the sd card root directory, download directory,
 `Android/data/`, `Android/obb/` will be inaccessible.

## About share

You can share a file or some text to this app (directory or empty file are not support)

## About unzip file

Txel can unzip a zip file and server it as a directory for you (it unzip the files to app's cache dir).
Just click unzip checkbox, it will appeared every time you choose a zip file.
For a zip file once unzipped, it won't delete it unless you unchecked the checkbox.
Try re-zip file if unzip file cause app crash.

## Upload file

The files been uploaded are save to phone's Download directory.
If the file name conflict, new file will be renamed.

## About QRCode generate

In home page enter "QRCode", make sure then parameters you enter is correct, or else it will malfunction.

See "[QRCode-kotlin](https://qrcodekotlin.com)"

## Configure

There are some options you can set.

1. Dark mode: dark light mode switch
2. Port: set the default port
3. MIME Type: customize your own MIME type (will take precedence).

**NOTE**: 
For MIME List, refer to [IANA Media Type](https://www.iana.org/assignments/media-types/media-types.xhtml).
In general, browser handle file by their MIME type, e.g. 'text/plain html',
this line will let any '*.html' file show as text in browser.

And some functions you may find useful one day.

1. Copy saved text: copy the text to clipboard.
2. Send clipboard: send clipboard text.
3. Extract cache (directory) as zip: rescue your data from cache. 

## Work With Curl

```
# download a file called 'temp.txt'
curl -o temp.txt "http://192.168.0.2:8080/d"

# upload a file called 'temp.txt', rename it to TEMP.txt
curl -F "file=@temp.txt" -F "file_name=TEMP.txt" -X POST "http://192.168.0.2:8080/u"

# get saved text on server
curl "http://192.168.0.2:8080/r"

# set saved text on server
curl -d "text=Some String" "http://192.168.0.2:8080/t" 
```

## Network unavailable ?

It will connect to `localhost` if your phone didn't have a network connection.

## Port in use ?

If you see a prompt tell you that port in use, that's mean the http server exit due to socket bind error.
Others is using this port. But Service not quit, you can see who is occupy your port.

## Connect reset ?

Restart server

## Javascript disabled ?

All the web pages can run with or without javascript.

# Credits

- [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd)
- [QRCode-kotlin](https://github.com/g0dkar/qrcode-kotlin)
