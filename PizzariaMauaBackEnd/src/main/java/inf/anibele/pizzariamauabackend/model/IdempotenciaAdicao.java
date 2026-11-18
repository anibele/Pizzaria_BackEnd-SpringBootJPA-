package inf.anibele.pizzariamauabackend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "idempotencia_adicao", uniqueConstraints = @UniqueConstraint(
        name = "uk_idempotencia_adicao", columnNames = {"usuario_id", "pedido_id", "chave"}))
@Getter
@Setter
public class IdempotenciaAdicao {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, foreignKey = @ForeignKey(name = "fk_idempotencia_usuario"))
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false, foreignKey = @ForeignKey(name = "fk_idempotencia_pedido"))
    private Pedido pedido;

    // UUID binario padrao, sem troca da ordem dos bytes.
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, length = 16)
    private UUID chave;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "payload_hash", nullable = false, length = 32)
    private byte[] payloadHash;

    @Column(name = "resposta_json", nullable = false, columnDefinition = "LONGTEXT")
    private String respostaJson;

    @Column(name = "status_http", nullable = false)
    private short statusHttp;

    @Column(name = "criado_em", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime criadoEm;

    @Column(name = "expira_em", columnDefinition = "DATETIME(6)")
    private LocalDateTime expiraEm;
}
