package com.booking.service;

import com.booking.advice.EntityAlreadyExistException;
import com.booking.advice.ResourceNotFoundException;
import com.booking.models.User;
import com.booking.repositories.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Validated
public class UserService {
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepo userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    public User getByUsername(String username){
        User userFromDb = userRepo.findByUsername(username);
        if (userFromDb == null) {
            throw new ResourceNotFoundException("An employee with this login: '" + username + "' not found");
        }
        return userFromDb;
    }

    public List<User> getAllUsers() {
        return userRepo.findAll();
    }

    public User getUserById(Long userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("An employee with ID " + userId + " not found"));
    }

    public User createUser(@Valid User user) {
        if (hasUserByLogin(user.getUsername())) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            userRepo.save(user);
        }
        return user;
    }

    public Map<String, Boolean> updateUser(Long userId, @Valid User userNew) {
        User userOld = getUserById(userId);
        if (userOld.getUsername().equals(userNew.getUsername()) || hasUserByLogin(userNew.getUsername())) {
            userOld.setFirstName(userNew.getFirstName());
            userOld.setLastName(userNew.getLastName());
            userOld.setUsername(userNew.getUsername());
            userOld.setPassword(passwordEncoder.encode(userNew.getPassword()));
            userOld.setRoles(userNew.getRoles());
            userRepo.save(userOld);
        }
        Map<String, Boolean> map = new HashMap<>();
        map.put("User parameters updated successfully", Boolean.TRUE);
        return map;
    }

    public Map<String, Boolean> deleteUserById(Long userId) {
        User user = getUserById(userId);
        userRepo.delete(user);
        Map<String, Boolean> map = new HashMap<>();
        map.put("User Deleted Successfully", Boolean.TRUE);
        return map;
    }

    private boolean hasUserByLogin(String login) {
        User userFromDb = userRepo.findByUsername(login);
        if (userFromDb != null) {
            throw new EntityAlreadyExistException("An employee with this login: '" + login + "' already exist!");
        }
        return true;
    }
}