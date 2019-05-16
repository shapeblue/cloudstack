// Virtual Appliance Agent
package main

import (
	"context"
	"log"
	"net"

	"google.golang.org/grpc"
	pb "./virtualappliance"
)

const (
	port = ":8200"
)

type server struct{}

func (s *server) Ping(ctx context.Context, in *pb.PingRequest) (*pb.PingResponse, error) {
	log.Printf("Received: %v", in.Message)
	return &pb.PingResponse{Message: "Pong " + in.Message}, nil
}

func main() {
	lis, err := net.Listen("tcp", port)
	if err != nil {
		log.Fatalf("failed to listen: %v", err)
	}
	s := grpc.NewServer()
	pb.RegisterApplianceAgentServer(s, &server{})
	if err := s.Serve(lis); err != nil {
		log.Fatalf("failed to serve: %v", err)
	}
}
