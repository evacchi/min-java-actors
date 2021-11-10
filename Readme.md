
To run with Maven (assuming you have JDK 17):

```
mvn compile exec:java@server
```

and in another shell:

```
mvn exec:java@client -Dexec.args="alice"
```

```
mvn exec:java@client -Dexec.args="bob"
```

for the async version:

```
mvn compile exec:java@asyncserver
```

and in another shell:

```
mvn exec:java@asyncclient -Dexec.args="alice"
```

```
mvn exec:java@asyncclient -Dexec.args="bob"
```

Run the async version with jbang:

```
jbang src/main/java/io/github/evacchi/asyncchat/ChatServer.java
```

and in another shell:

```
jbang jbang src/main/java/io/github/evacchi/asyncchat/ChatClient.java alice
```

```
jbang src/main/java/io/github/evacchi/asyncchat/ChatClient.java bob
```
