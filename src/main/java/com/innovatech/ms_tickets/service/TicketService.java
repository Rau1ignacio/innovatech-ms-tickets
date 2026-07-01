package com.innovatech.ms_tickets.service;

import com.innovatech.ms_tickets.dto.request.TicketRequestDTO;
import com.innovatech.ms_tickets.dto.request.TicketUpdateRequestDTO;
import com.innovatech.ms_tickets.dto.response.TicketCreatedEventDTO;
import com.innovatech.ms_tickets.dto.response.TicketResponseDTO;
import com.innovatech.ms_tickets.exception.TicketNotFoundException;
import com.innovatech.ms_tickets.model.Ticket;
import com.innovatech.ms_tickets.model.enums.Estado;
import com.innovatech.ms_tickets.model.enums.Prioridad;
import com.innovatech.ms_tickets.repository.TicketRepository;
import com.innovatech.ms_tickets.security.AuthenticatedUser;
import com.innovatech.ms_tickets.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketService {

    private final TicketRepository ticketRepository;
    private final RabbitTemplate rabbitTemplate;
    private final SecurityUtils securityUtils;

    @Value("${rabbitmq.exchange.name}")
    private String exchangeName;

    @Value("${rabbitmq.routing.key}")
    private String routingKey;

    @Transactional
    public TicketResponseDTO crearTicket(TicketRequestDTO requestDTO, AuthenticatedUser currentUser) {
        Long resolvedUserId = resolveTargetUserId(requestDTO.getUsuarioId(), currentUser);
        log.info("Creando ticket para usuario autenticado: {} (request usuarioId={})", currentUser.userId(), requestDTO.getUsuarioId());

        Ticket ticket = Ticket.builder()
                .usuarioId(resolvedUserId)
                .asunto(requestDTO.getAsunto())
                .descripcion(requestDTO.getDescripcion())
                .estado(Estado.ABIERTO)
                .prioridad(requestDTO.getPrioridad())
                .build();

        Ticket savedTicket = ticketRepository.save(ticket);
        log.info("Ticket guardado con ID: {}", savedTicket.getId());

        TicketResponseDTO responseDTO = mapToResponseDTO(savedTicket);

        publicarEventoTicketCreado(responseDTO);

        return responseDTO;
    }

    @Transactional(readOnly = true)
    public List<TicketResponseDTO> listarHistorialPorUsuario(Long usuarioId, AuthenticatedUser currentUser) {
        securityUtils.requireOwnerOrAdmin(currentUser, usuarioId);
        log.info("Listando historial de tickets para usuario: {}", usuarioId);

        return ticketRepository.findByUsuarioId(usuarioId)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TicketResponseDTO obtenerTicketPorId(Long id, AuthenticatedUser currentUser) {
        log.info("Buscando ticket con ID: {}", id);

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));
        securityUtils.requireOwnerOrAdmin(currentUser, ticket.getUsuarioId());

        return mapToResponseDTO(ticket);
    }

    @Transactional
    public TicketResponseDTO actualizarEstadoYPrioridad(Long id, TicketUpdateRequestDTO requestDTO, AuthenticatedUser currentUser) {
        log.info("Actualizando ticket ID: {} - Estado: {} - Prioridad: {}",
                id, requestDTO.getEstado(), requestDTO.getPrioridad());

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException(id));

        if (!currentUser.hasRole("ADMIN")) {
            securityUtils.requireOwnerOrAdmin(currentUser, ticket.getUsuarioId());
        }

        ticket.setEstado(requestDTO.getEstado());
        ticket.setPrioridad(requestDTO.getPrioridad());

        if (requestDTO.getEstado() == Estado.CERRADO || requestDTO.getEstado() == Estado.RESUELTO) {
            ticket.setFechaCierre(LocalDateTime.now());
        }

        Ticket updatedTicket = ticketRepository.save(ticket);
        log.info("Ticket ID: {} actualizado exitosamente", updatedTicket.getId());

        return mapToResponseDTO(updatedTicket);
    }

    @Transactional(readOnly = true)
    public List<TicketResponseDTO> listarTodosLosTickets() {
        log.info("Listando todos los tickets");

        return ticketRepository.findAll()
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TicketResponseDTO> listarPorEstado(Estado estado) {
        log.info("Listando tickets por estado: {}", estado);
        return ticketRepository.findByEstado(estado)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TicketResponseDTO> listarPorPrioridad(Prioridad prioridad) {
        log.info("Listando tickets por prioridad: {}", prioridad);
        return ticketRepository.findByPrioridad(prioridad)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    public List<TicketResponseDTO> listarMisTickets(AuthenticatedUser currentUser) {
        return listarHistorialPorUsuario(currentUser.userId(), currentUser);
    }

    private Long resolveTargetUserId(Long requestUserId, AuthenticatedUser currentUser) {
        if (currentUser.hasRole("ADMIN")) {
            if (requestUserId == null) {
                throw new IllegalArgumentException("usuarioId es obligatorio para operaciones administrativas");
            }
            return requestUserId;
        }

        if (requestUserId != null && !Objects.equals(requestUserId, currentUser.userId())) {
            log.warn("Se ignoró usuarioId={} enviado por frontend; se usará el usuario autenticado={}", requestUserId, currentUser.userId());
        }

        return currentUser.userId();
    }

    private void publicarEventoTicketCreado(TicketResponseDTO ticketDTO) {
        try {
            TicketCreatedEventDTO eventDTO = TicketCreatedEventDTO.fromTicketResponse(ticketDTO);

            log.info("Publicando evento Ticket_Creado para ticket ID: {}", eventDTO.getTicketId());
            rabbitTemplate.convertAndSend(exchangeName, routingKey, eventDTO);
            log.info("Evento publicado exitosamente");

        } catch (Exception e) {
            log.error("Error al publicar evento Ticket_Creado: {}", e.getMessage(), e);
        }
    }

    private TicketResponseDTO mapToResponseDTO(Ticket ticket) {
        return TicketResponseDTO.builder()
                .id(ticket.getId())
                .usuarioId(ticket.getUsuarioId())
                .asunto(ticket.getAsunto())
                .descripcion(ticket.getDescripcion())
                .estado(ticket.getEstado())
                .prioridad(ticket.getPrioridad())
                .fechaCreacion(ticket.getFechaCreacion())
                .fechaActualizacion(ticket.getFechaActualizacion())
                .fechaCierre(ticket.getFechaCierre())
                .build();
    }
}
