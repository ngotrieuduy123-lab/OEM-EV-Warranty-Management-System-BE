package com.mega.warrantymanagementsystem.service;


import com.mega.warrantymanagementsystem.entity.Account;
import com.mega.warrantymanagementsystem.model.response.AccountResponse;
import com.mega.warrantymanagementsystem.repository.AccountRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Service
public class TokenService {

    @Autowired
    AccountRepository accountRepository;

    @Value("${jwt.secret}")
    private String SECRET_KEY;

    public SecretKey getSignInKey(){
//        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        byte[] keyBytes = SECRET_KEY.getBytes(); // không decode base64
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Account account){
        return Jwts.builder()
                .subject(account.getAccountId() + "")
//                .claim("username", account.getUsername()) // thêm username
//                .claim("roleName", account.getRole().getRoleName().name()) // thêm roleName
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 7))
                .signWith(getSignInKey())
                .compact();
    }

    //verify token
    public Account extractToken(String token) {
        String value = extractClaim(token, Claims::getSubject);
        return accountRepository.findByAccountId(value);
    }

//    public Account extractToken(String token) {
//        String cleanToken = token.replace("Bearer ", "");
//        Claims claims = extractAllClaims(cleanToken);
//
//        String accountId = claims.getSubject();
//        String username = claims.get("username", String.class);
//        String roleName = claims.get("roleName", String.class);
//
//        // tìm trong DB bằng accountId
//        Account account = accountRepository.findByAccountId(accountId);
//        if (account != null) {
//            // Nếu muốn, cập nhật lại username hoặc roleName từ token
//            // account.setUsername(username);
//            // account.getRole().setRoleName(Role.RoleName.valueOf(roleName));
//        }
//        return account;
//    }


    public Claims extractAllClaims(String token) {
        return  Jwts.parser().
                verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }


//    public Claims extractAllClaims(String token) {
//        String cleanToken = token.replace("Bearer ", "");
//        return Jwts.parser()
//                .verifyWith(getSignInKey())
//                .build()
//                .parseSignedClaims(cleanToken)
//                .getPayload();
//    }


    public <T> T extractClaim(String token, Function<Claims,T> resolver){
        Claims claims = extractAllClaims(token);
        return  resolver.apply(claims);
    }

//    // ------------------ Mới thêm ------------------
//    // Lấy roleName từ token
//    public String getRoleNameFromToken(String token) {
//        Claims claims = extractAllClaims(token);
//        return claims.get("roleName", String.class);
//    }
//
//    // Lấy username từ token
//    public String getUsernameFromToken(String token) {
//        Claims claims = extractAllClaims(token);
//        return claims.get("username", String.class);
//    }

}
