package main

import (
	"context"
	"log"
	"os"
	"time"

	"google.golang.org/grpc"
	pb "./virtualappliance"
)

const (
	address     = "localhost:50051"
	defaultName = "client123"
)

func main() {
	conn, err := grpc.Dial(address, grpc.WithInsecure())
	if err != nil {
		log.Fatalf("did not connect: %v", err)
	}
	defer conn.Close()
	c := pb.NewApplianceAgentClient(conn)

	name := defaultName
	if len(os.Args) > 1 {
		name = os.Args[1]
	}
	ctx, cancel := context.WithTimeout(context.Background(), time.Second)
	defer cancel()
	r, err := c.Ping(ctx, &pb.PingRequest{Message: name})
	if err != nil {
		log.Fatalf("could not ping due to: %v", err)
	}
	log.Printf("Pinging: %s", r.Message)
}
