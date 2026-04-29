package com.example.demo.security;

import com.example.demo.app.domain.AppUser;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AuthUser implements UserDetails {

    private final AppUser user;

    public AuthUser(AppUser user) {
        this.user = user;
    }

    public AppUser getUser() {
        return user;
    }

    public UUID getUserId() {
        return user.getUserId();
    }

    public String getRoleCode() {
        return user.getRoleCode();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRoleCode()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }
}
