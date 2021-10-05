package io.github.evacchi.chat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Scanner;

// wrappers that throw UncheckedIOExceptions

class ChatServerSocket {
    int port;
    ServerSocket serverSocket;

    public ChatServerSocket(int port) {
        this.port = port;
        try {
            this.serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public int getLocalPort() {
        return serverSocket.getLocalPort();
    }

    public SocketAddress getLocalSocketAddress() {
        return serverSocket.getLocalSocketAddress();
    }

    public ChatSocket accept() {
        try {
            return new ChatSocket(serverSocket.accept());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

class ChatSocket {
    private final Scanner inputScanner;
    private final PrintWriter outputWriter;
    private final Socket socket;

    public ChatSocket(Socket socket) {
        try {
            this.socket = socket;
            this.inputScanner = new Scanner(socket.getInputStream());
            this.outputWriter = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public ChatSocket(String host, int portNumber) {
        try {
            this.socket = new Socket(host, portNumber);
            this.inputScanner = new Scanner(socket.getInputStream());
            this.outputWriter = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    public SocketAddress getRemoteSocketAddress() {
        return socket.getRemoteSocketAddress();
    }

    public PrintWriter getOutputWriter() {
        return outputWriter;
    }

    public Scanner getInputScanner() {
        return inputScanner;
    }

    public int getLocalPort() {
        return socket.getLocalPort();
    }

    public int getPort() {
        return socket.getPort();
    }
}