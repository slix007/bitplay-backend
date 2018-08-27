package com.bitplay.security;

import com.bitplay.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@Configuration
@EnableWebSecurity
class WebSecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private CustomBasicAuthenticationEntryPoint customBasicAuthenticationEntryPoint;

    @Autowired
    private Config config;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .antMatchers("/api/auth/**").permitAll()
                .and().httpBasic() //
                .authenticationEntryPoint(customBasicAuthenticationEntryPoint)
                .and().csrf().disable();
    }

    @Autowired
    void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
                .withUser("trader").password(config.getUiPasswordForTrader() != null ? config.getUiPasswordForTrader() : "password").authorities("ROLE_TRADER")
                .and()
                .withUser("admin").password(config.getUiPasswordForAdmin() != null ? config.getUiPasswordForAdmin() : "password")
                .authorities("ROLE_TRADER", "ROLE_ADMIN");

    }

    @Bean
    public CustomBasicAuthenticationEntryPoint getBasicAuthEntryPoint() {
        return new CustomBasicAuthenticationEntryPoint();
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers(HttpMethod.OPTIONS, "/**");
    }

}
