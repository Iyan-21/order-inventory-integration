package edu.cit.Abesia.channel;

import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/** Serialises everything that changes channel state: feed events and backorder resolution. */
@Component
class ChannelLock {
    final ReentrantLock lock = new ReentrantLock();
}
