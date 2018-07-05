package com.bitplay.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.savedrequest.NullRequestCache;

@EnableWebSecurity
class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests() //
                .antMatchers("/stub/**").access("hasRole('ROLE_ADMIN')")
//                .antMatchers("/api/auth/**").access("hasRole('ROLE_USER')")
//                .anyRequest().authenticated() //
                .and().requestCache().requestCache(new NullRequestCache()) //
                .and().httpBasic() //
                .and().csrf().disable();
    }

    @Autowired
    void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication() //
                .withUser("user").password("password").authorities("ROLE_USER") //
                .and()
                .withUser("trader").password("password").authorities("ROLE_USER", "ROLE_TRADER") //
                .and() //
                .withUser("admin").password("password").authorities("ROLE_USER", "ROLE_TRADER", "ROLE_ADMIN");

    }
}
