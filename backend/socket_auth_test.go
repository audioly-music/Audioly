package main

import (
	"net/http/httptest"
	"testing"
)

func TestBrowserSocketToken(t *testing.T) {
	r := httptest.NewRequest("GET", "/ws/parties/ABC123", nil)
	r.Header.Set("Sec-WebSocket-Protocol", "audioly.v1, audioly.token.browser-token")
	if got := parseSocketToken(r); got != "browser-token" {
		t.Fatalf("token = %q", got)
	}
	if got := parseBearerToken(r); got != "" {
		t.Fatal("REST must not accept socket credentials")
	}
	r.Header.Set("Authorization", "Bearer native-token")
	if got := parseSocketToken(r); got != "native-token" {
		t.Fatal("native header must remain supported")
	}
	r.Header.Del("Authorization")
	r.Header.Set("Sec-WebSocket-Protocol", "other.v1")
	if got := parseSocketToken(r); got != "" {
		t.Fatal("unexpected protocol accepted")
	}
}
