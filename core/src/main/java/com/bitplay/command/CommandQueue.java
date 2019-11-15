package com.bitplay.command;

import org.springframework.stereotype.Component;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;

@Component
public class CommandQueue {

    private TransferQueue<Command> queue = new LinkedTransferQueue<>();

}
