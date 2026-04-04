package com.plantogether.trip.config;

import com.plantogether.trip.grpc.server.TripGrpcServiceImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class GrpcServerConfig implements SmartLifecycle {

    @Value("${grpc.server.port:9081}")
    private int grpcPort;

    private final TripGrpcServiceImpl tripGrpcService;
    private Server server;
    private volatile boolean running = false;

    public GrpcServerConfig(TripGrpcServiceImpl tripGrpcService) {
        this.tripGrpcService = tripGrpcService;
    }

    @Override
    public void start() {
        try {
            server = ServerBuilder.forPort(grpcPort)
                .addService(tripGrpcService)
                .build()
                .start();
            running = true;
            log.info("gRPC server started on port {}", grpcPort);
        } catch (IOException e) {
            throw new RuntimeException("Failed to start gRPC server on port " + grpcPort, e);
        }
    }

    @Override
    public void stop() {
        if (server != null && !server.isShutdown()) {
            server.shutdown();
            try {
                if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                server.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        running = false;
        log.info("gRPC server stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
