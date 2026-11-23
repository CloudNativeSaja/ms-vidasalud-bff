package cl.duoc.vidasalud.bff.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Matriz de roles del catalogo. El microservicio catalog no esta levantado:
 * un usuario autorizado recibe 503 del reenvio, y uno sin el rol recibe 403
 * antes de llegar a el. Asi se comprueba que la API (no solo la pantalla)
 * impide que un recepcionista modifique el catalogo.
 */
@SpringBootTest
@TestPropertySource(properties = "services.catalog.url=http://127.0.0.1:1")
class CatalogRolesSecurityTest {

    private static final String JSON = "{\"name\":\"x\"}";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private static RequestPostProcessor rol(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/bff/catalog/services")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/bff/catalog/services/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void cualquierUsuarioAutenticadoPuedeConsultarElCatalogo() throws Exception {
        for (String role : new String[] {"ADMIN", "RECEPCIONISTA", "AUDITOR", "PACIENTE"}) {
            mockMvc.perform(get("/api/bff/catalog/services").with(rol(role))).andExpect(status().isServiceUnavailable());
            mockMvc.perform(get("/api/bff/catalog/boxes").with(rol(role))).andExpect(status().isServiceUnavailable());
            mockMvc.perform(get("/api/bff/catalog/slots").with(rol(role))).andExpect(status().isServiceUnavailable());
        }
    }

    @Test
    void soloAdminCreaEditaYEliminaPrestacionesBoxesYHorarios() throws Exception {
        for (String base : new String[] {"services", "boxes", "slots"}) {
            String url = "/api/bff/catalog/" + base;

            mockMvc.perform(post(url).with(rol("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(JSON))
                    .andExpect(status().isServiceUnavailable());
            mockMvc.perform(put(url + "/1").with(rol("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(JSON))
                    .andExpect(status().isServiceUnavailable());
            mockMvc.perform(delete(url + "/1").with(rol("ADMIN"))).andExpect(status().isServiceUnavailable());

            for (String role : new String[] {"RECEPCIONISTA", "AUDITOR", "PACIENTE"}) {
                mockMvc.perform(post(url).with(rol(role)).contentType(MediaType.APPLICATION_JSON).content(JSON))
                        .andExpect(status().isForbidden());
                mockMvc.perform(put(url + "/1").with(rol(role)).contentType(MediaType.APPLICATION_JSON).content(JSON))
                        .andExpect(status().isForbidden());
                mockMvc.perform(delete(url + "/1").with(rol(role))).andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void reservarYLiberarUnCupoLoPuedenAdminYRecepcion() throws Exception {
        for (String accion : new String[] {"reserve", "release"}) {
            String url = "/api/bff/catalog/slots/1/" + accion;

            mockMvc.perform(put(url).with(rol("ADMIN"))).andExpect(status().isServiceUnavailable());
            mockMvc.perform(put(url).with(rol("RECEPCIONISTA"))).andExpect(status().isServiceUnavailable());
            mockMvc.perform(put(url).with(rol("AUDITOR"))).andExpect(status().isForbidden());
            mockMvc.perform(put(url).with(rol("PACIENTE"))).andExpect(status().isForbidden());
        }
    }
}
