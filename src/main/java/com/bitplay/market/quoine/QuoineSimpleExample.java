package com.bitplay.market.quoine;

//import io.jsonwebtoken.JwtBuilder;
//import io.jsonwebtoken.Jwts;
//import io.jsonwebtoken.SignatureAlgorithm;
//import io.restassured.RestAssured;
//import io.restassured.response.Response;

/**
 * Created by Sergey Shurmin on 3/20/17.
 */
public class QuoineSimpleExample extends QuoineBase {

    public void doTheWork() {
//        String path = "/orders?product_id=1";
//        final String token = createToken(path);
//
//        useRestAssured(path, token);

    }
/*
    private void useRestAssured(String path, String token) {
        RestAssured.baseURI = BASE_URL;

        final Response response = RestAssured.given()
                .contentType("application/json")
                .header("X-QuoineBase-API-Version", "2")
                .header("X-QuoineBase-Auth", token)
                .get(path);

        System.out.println(response.getBody().asString());
        System.out.println(response.getStatusCode());
    }

    class AuthPayload {
        String path;
        String nonce;
        String token_id;
    }

    private String createToken(String path) {

        final String nonce = String.valueOf(System.nanoTime());
//                        .withClaim("nonce", String.valueOf(System.nanoTime()))



        final AuthPayload authPayload = new AuthPayload();
        authPayload.token_id = TOKEN_ID;
        authPayload.nonce = nonce;
        authPayload.path = path;

//        final Claims claims = Claims.
        final JwtBuilder builder = Jwts.builder();
        builder.claim("token_id", TOKEN_ID);
        builder.claim("path", path);
        builder.claim("nonce", nonce);
        builder.signWith(SignatureAlgorithm.HS256, TOKEN_SECRET.getBytes());
        final String compact = builder.compact();
        System.out.println(compact);

        return compact;
    }

*/
}
