package com.collapp.project.service.impl;

import com.collapp.project.dto.auth.AuthResponse;
import com.collapp.project.dto.auth.LoginRequest;
import com.collapp.project.dto.auth.RegisterRequest;
import com.collapp.project.entity.User;
import com.collapp.project.entity.enums.SystemRole;
import com.collapp.project.repository.UserRepository;
import com.collapp.project.mapper.UserMapper;
import com.collapp.project.security.CustomUserDetails;
import com.collapp.project.security.JwtService;
import com.collapp.project.util.TestObjectFactory;
import jakarta.persistence.EntityExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthServiceImpl authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User user;
    private CustomUserDetails userDetails;
    private static final String JWT_TOKEN = "test.jwt.token";

    @BeforeEach
    void setUp() {
        registerRequest = TestObjectFactory.createRegisterRequest();
        loginRequest = TestObjectFactory.createLoginRequest();
        user = TestObjectFactory.createUser();
        userDetails = new CustomUserDetails(user);
    }

    @Test
    void registra_un_usuario_y_devuelve_el_token() {
        when(userRepository.existsByUsername(registerRequest.username())).thenReturn(false);
        when(userRepository.existsByEmail(registerRequest.email())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_password");

        // El mapper real construye el usuario a partir del request, así que el stub debe ser coherente con él.
        User registered = User.builder()
                .username(registerRequest.username())
                .fullName(registerRequest.fullName())
                .email(registerRequest.email())
                .passwordHash("encoded_password")
                .specialty(registerRequest.specialty())
                .systemRole(SystemRole.ROLE_CREATIVE)
                .build();

        when(userMapper.toEntity(registerRequest, "encoded_password")).thenReturn(registered);
        when(userRepository.save(any(User.class))).thenReturn(registered);
        when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn(JWT_TOKEN);

        AuthResponse result = authService.register(registerRequest);

        assertNotNull(result);
        assertEquals(JWT_TOKEN, result.token());
        assertEquals(registerRequest.username(), result.username());
        assertEquals(SystemRole.ROLE_CREATIVE.name(), result.role());
        verify(userMapper).toEntity(registerRequest, "encoded_password");
        verify(userRepository).save(any(User.class));
        verify(jwtService).generateToken(any(CustomUserDetails.class));
    }

    @Test
    void lanza_conflicto_si_el_username_ya_esta_en_uso() {
        when(userRepository.existsByUsername(registerRequest.username())).thenReturn(true);

        assertThrows(EntityExistsException.class,
                () -> authService.register(registerRequest));

        verify(userRepository, never()).save(any());
    }

    @Test
    void lanza_conflicto_si_el_email_ya_esta_registrado() {
        when(userRepository.existsByUsername(registerRequest.username())).thenReturn(false);
        when(userRepository.existsByEmail(registerRequest.email())).thenReturn(true);

        assertThrows(EntityExistsException.class,
                () -> authService.register(registerRequest));

        verify(userRepository, never()).save(any());
    }

    @Test
    void inicia_sesion_y_devuelve_el_token() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(jwtService.generateToken(userDetails)).thenReturn(JWT_TOKEN);

        AuthResponse result = authService.login(loginRequest);

        assertNotNull(result);
        assertEquals(JWT_TOKEN, result.token());
        assertEquals(user.getUsername(), result.username());
        assertEquals(SystemRole.ROLE_CREATIVE.name(), result.role());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtService).generateToken(userDetails);
    }

    @Test
    void lanza_excepcion_con_credenciales_invalidas() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class,
                () -> authService.login(loginRequest));

        verify(jwtService, never()).generateToken(any());
    }
}
