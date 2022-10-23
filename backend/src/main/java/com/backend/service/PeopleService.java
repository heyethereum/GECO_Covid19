package com.backend.service;

import com.backend.model.PeopleModel;
import com.backend.repo.PeopleRepo;
import com.backend.request.PeopleRequest;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Service
public class PeopleService {

    @Autowired
    PeopleRepo peopleRepo;

    @Autowired
    Environment environment;

    public List<PeopleModel> listPeople() {
        return peopleRepo.findAll();
    }

    public void createUser(PeopleRequest peopleRequest) throws Exception {

        Optional<PeopleModel> userExist = peopleRepo.getPeopleByEmail(peopleRequest.getEmail());
        if (userExist.isPresent()) {
            throw new Exception("Email already exists.");
        }
        PeopleModel peopleNew = PeopleModel.builder()
                .lastName(peopleRequest.getLastName())
                .firstName(peopleRequest.getFirstName())
                .email(peopleRequest.getEmail())
                .password(peopleRequest.getPassword())
                .address(peopleRequest.getAddress())
                .postcode(peopleRequest.getPostcode())
                .phone(peopleRequest.getPhone())
                .dob(peopleRequest.getDob())
                .officialId(peopleRequest.getOfficialId())
                .role(Objects.isNull(peopleRequest.getRole()) ? PeopleModel.Role.user : peopleRequest.getRole())
                .build();

        peopleRepo.save(peopleNew);
    }

    public Jws<Claims> validateJWT(String token) {
        return Jwts.parser().setSigningKey(environment.getProperty("JWT_SECRET")).parseClaimsJws(token);
//        return true;
    }

    public boolean validateToken(String token, Long peopleId) throws Exception {
        PeopleModel user = peopleRepo.findById(peopleId).orElseThrow(
                () -> new Exception("UserID not found"));
        if (user.getToken().equals(token)) {
            return true;
        } else {
            throw new Exception("Token not match");
        }
    }

    public Long getIdByToken(String token) throws NumberFormatException {
        return Long.valueOf((String) validateJWT(token).getBody().get("jti"));
    }

    public PeopleModel loginValidate(String email, String password) throws Exception {
        Optional<PeopleModel> peopleOpt = peopleRepo.getPeopleByEmailAndPassword(email, password);
        if (peopleOpt.isPresent()) {
            PeopleModel people = peopleOpt.get();
//            String token = genTokenForEmail(email);
            String token = genJWT(people, 1, 0);
            updateTokenById(token, people.getId());
            people.setToken(token);

            ZoneId zid = ZoneId.of("Asia/Singapore");
            ZonedDateTime dtLogin = ZonedDateTime.now(zid);
            updateLastLoginById(dtLogin, people.getId());
            people.setLastLogin(dtLogin);

            return people;
        } else {
            throw new Exception("Please provide correct Email and Password.");
        }
    }

    private String genJWT(PeopleModel people, int hour, int min) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, min);
        cal.add(Calendar.HOUR, hour);
        return Jwts.builder()
                .claim("email", people.getEmail())
                .setSubject(people.getFirstName() + "_" + people.getLastName())
                .setId(String.valueOf(people.getId()))
                .setIssuedAt(new Date())
                .setExpiration(cal.getTime())
                .signWith(SignatureAlgorithm.HS512, environment.getProperty("JWT_SECRET"))
                .compact();
    }

    private void updateTokenById(String token, Long peopleId) throws Exception {
        try {
            peopleRepo.updateTokenByPeopleId(token, peopleId);
        } catch (Exception e) {
            throw new Exception("Update fail");
        }
    }

    private void updateLastLoginById(ZonedDateTime dtLogin, Long peopleId) throws Exception {
        try {
            peopleRepo.updateLastLoginByPeopleId(dtLogin, peopleId);
        } catch (Exception e) {
            throw new Exception("Update fail");
        }
    }

    public PeopleModel getPeopleById(Long peopleId) throws Exception {
        return peopleRepo.findById(peopleId).orElseThrow(() -> new Exception("UserID not found"));
    }


}
