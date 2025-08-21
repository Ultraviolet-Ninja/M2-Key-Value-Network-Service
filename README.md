# M2 Key-Value Network Service Project

**CSC 6712 - Project 2: Key-Value Network Service**

Grading Option: **A-Level**

---

## Overview

This project uses a B-Tree from the last project and hides it in a server that takes specific commands to interact with
the B-Tree (e.g. read, write, check for specific key). The server uses a `tree-log.txt` file to act as the persistent
storage of key-value pairs for B-Tree. If the server shuts down, it relies on this to recover stored data. Transactions
use Logger `INFO` levels to write out transaction data (locked keys, client ID, transaction operation). 
Clients can connect to the server to conduct operations on the B-Tree and can be seen in the `.\logs` folder.

### Supported Commands

- `PUT [KEY] [VALUE]` — Inserts or updates a key-value pair on the server, then return the previous key or `null`
- `GET [KEY]` — Retrieves the value for a given key from the server with `null` being the response for an empty key
- `CONTAINS [KEY]` — Checks whether a key exists on the server
- `TRANSACT [KEY-1] ...` - Starts a transaction on the server for the specified keys by locking them and providing the 
client with the expiration time of the transaction. (_Transaction lifetime is 15 minutes_)
- `COMMIT` - If a transaction is in process, conducts all valid `PUT`, `GET` and `CONTAINS` commands submitted
to the server after the `TRANSACT`, else they'll be rejected commands.
  - Valid commands meaning formatted correctly and use only the keys that were locked by the `TRANSACT`.
- `ABORT` - If a transaction is in process, backs out of the transaction and releases all held keys
- `SHUTDOWN` - Starts a shutdown process on the server. Any client with a prior valid transaction request can still 
conduct actions on the server until they commit, abort or let the transaction expire. After that, the 
server will reject that client's further requests.
- `EXIT` - Disconnects from the server and exits the client

\* 
All commands can be specified with any casing, but KEYS and VALUES are case-sensitive.
---

## Requirements
- Java 17

### Starting up the Project via Source Code OR Jar File
To get started with the source code, make sure to download the version of Java needed for the project and download
the code. Using the Linux command line, use `chmod +x gradlew` then `./gradlew run` to boot up the project
**OR** `./gradlew test`.
- For **Windows**, it'll just be `gradlew.bat run` **OR** `gradlew.bat test`

This should kickstart the dependency download and build process.

#### Argument Flags
Conducting `./gradlew run [OPTIONAL-PORT]` without any flags will start the code as a server. The user can specify a
port to use for the server to communicate on.

Conducting `./gradlew run -c [OPTIONAL IP:PORT]` OR `./gradlew run --client [OPTIONAL IP:PORT]` will run the code as a client.
A second argument can be provided to specify the PORT number that the server lives on, but will assume `127.0.0.1:8080`
as the default connection.

Conducting `./gradlew run -pc` will generate 1 million key value pairs to run in **Gauntlet Mode**.
This mode runs 5 clients that'll all read from the same server to benchmark average client servicing time.

Conducting `./gradlew run -load-mil` runs a server that loads in the 1 million key-value pairs.

Conducting `./gradlew run -g [OPTIONAL-IP]` runs the Gauntlet. Can specify the IP address if the server is on a
different IP.

#### Procedure
First, set up a server, then the client(s).

**1 server:1 client** or **1 client:N clients** will go `./gradlew run` on one terminal and `./gradlew run -c`
on one or more terminals.

**Gauntlet Mode**: Server starts with `./gradlew run -load-mil` and conduct `./gradlew run -g` for the client.
