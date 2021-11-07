package io.github.evacchi.chat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public interface BlockingChat {
    class Server {
        ServerSocket serverSocket;
        List<ClientHandler> clientHandlers;
        Server(int port) throws IOException {
            serverSocket = new ServerSocket(port);
            clientHandlers = new ArrayList<>();
        }
        void start() throws IOException {
            while (true) {
                // blocks until a new connection is established
                var clientSocket = serverSocket.accept();
                // then for each clientSocket...
                var handler = new ClientHandler(clientSocket, this);
                clientHandlers.add(handler);
                /* start handler.read() in a separate thread */
            }
        }
        // called by clientHandlers that want to propagate
        // messages to all the connected clients
        void broadcast(String msg) {
            for (var handler: clientHandlers) {
                handler.write(msg);
            }
        }
    }
    class ClientHandler {
        Server parent; BufferedReader in; PrintWriter out;

        ClientHandler(Socket clientSocket, Server server) throws IOException {
            // get the in/out streams from the socket
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            // keep a handle to the server to propagate messages back
            parent = server;
        }
        // on its own thread
        void read() throws IOException {
            while (in.ready()) {
                var line = validate(in.readLine());
                // for each line, broadcast to all other connected clients
                parent.broadcast(line);
            }
        }

        String validate(String readLine) {
            // validate here
            return readLine;
        }

        // called by server.broadcast()
        void write(String msg) {
            out.println(msg);
        }
    }

    class Client {
        Socket socket; BufferedReader userInput, socketInput; PrintWriter socketOutput;
        Client(String host, int port) throws IOException {
            this.socket = new Socket(host, port);
            this.userInput = new BufferedReader(new InputStreamReader(System.in));
            this.socketInput = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.socketOutput = new PrintWriter(socket.getOutputStream(), true);
        }
        // on its own thread
        void readUserInput() throws IOException {
            while (userInput.ready()) {
                var line = parse(userInput.readLine());
                write(line);
            }
        }


        // on its own thread
        void readServerInput() throws IOException {
            while (userInput.ready()) {
                var line = serialize(userInput.readLine());
                System.out.println(line); // echo to the user
                write(line);
            }
        }
        void write(String line) { socketOutput.println(line); }

        private String serialize(String line) { /* serialize */ return line; }
        private String parse(String line) { /* parse line */ return line; }

    }


}
