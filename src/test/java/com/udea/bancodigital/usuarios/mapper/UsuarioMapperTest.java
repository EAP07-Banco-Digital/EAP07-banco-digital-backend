package com.udea.bancodigital.usuarios.mapper;

import com.udea.bancodigital.usuarios.dto.PerfilUsuarioDTO;
import com.udea.bancodigital.usuarios.entity.Cliente;
import com.udea.bancodigital.usuarios.entity.Rol;
import com.udea.bancodigital.usuarios.entity.Usuario;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UsuarioMapperTest {

    private final UsuarioMapper mapper = UsuarioMapper.INSTANCE;

    private Cliente cliente() {
        Cliente cliente = new Cliente();
        cliente.setId(10L);
        cliente.setNombres("Juan Manuel");
        cliente.setApellidos("Tabares");
        cliente.setTipoDocumento("CC");
        cliente.setNumeroDocumento("1017654321");
        cliente.setEmail("juan@banco.com");
        cliente.setTelefono("3001234567");
        cliente.setDireccion("Calle 10 #20-30");
        cliente.setFechaRegistro(OffsetDateTime.parse("2026-09-20T10:00:00-05:00"));
        return cliente;
    }

    private Usuario usuario() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setEmail("juan@banco.com");
        usuario.setPasswordHash("$2a$10$hashSecretoQueNuncaDebeSalir");
        usuario.setFechaNacimiento(LocalDate.of(1998, 4, 15));
        usuario.setClienteId(10L);
        usuario.setRol(new Rol(1L, "CLIENTE"));
        return usuario;
    }

    @Test
    void elPerfilUneNombresYApellidosDelCliente() {
        PerfilUsuarioDTO perfil = mapper.toDTO(usuario(), cliente());

        assertThat(perfil.getNombre()).isEqualTo("Juan Manuel Tabares");
    }

    @Test
    void elPerfilTomaElIdYElCorreoDelUsuario() {
        PerfilUsuarioDTO perfil = mapper.toDTO(usuario(), cliente());

        assertThat(perfil.getId()).isEqualTo(1L);
        assertThat(perfil.getEmail()).isEqualTo("juan@banco.com");
    }

    @Test
    void elDocumentoSeOfuscaDejandoSoloLosUltimosCuatroDigitos() {
        PerfilUsuarioDTO perfil = mapper.toDTO(usuario(), cliente());

        assertThat(perfil.getDocumentoIdentidad()).isEqualTo("***4321");
        assertThat(perfil.getDocumentoIdentidad()).doesNotContain("1017654");
    }

    @Test
    void unDocumentoCortoSeOfuscaCompleto() {
        assertThat(UsuarioMapper.ofuscarDocumento("1234")).isEqualTo("***1234");
    }

    @Test
    void unDocumentoNuloSeQuedaEnNulo() {
        assertThat(UsuarioMapper.ofuscarDocumento(null)).isNull();
    }

    @Test
    void laOfuscacionIgnoraLosEspaciosDelDocumento() {
        assertThat(UsuarioMapper.ofuscarDocumento("  1017654321  ")).isEqualTo("***4321");
    }

    @Test
    void elPerfilNuncaExponeElHashDeLaContrasena() {
        PerfilUsuarioDTO perfil = mapper.toDTO(usuario(), cliente());

        // El DTO de salida no tiene dondealojarlo: la entidad nunca se serializa.
        assertThat(PerfilUsuarioDTO.class.getDeclaredFields())
                .extracting("name")
                .doesNotContain("passwordHash");
    }

    @Test
    void elPerfilDevuelveLosDatosDeContactoDelCliente() {
        PerfilUsuarioDTO perfil = mapper.toDTO(usuario(), cliente());

        assertThat(perfil.getTelefono()).isEqualTo("3001234567");
        assertThat(perfil.getDireccion()).isEqualTo("Calle 10 #20-30");
        assertThat(perfil.getFechaNacimiento()).isEqualTo(LocalDate.of(1998, 4, 15));
    }
}
