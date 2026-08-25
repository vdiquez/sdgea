# Spec · Bounded Context: Seguridad y Acceso

| Campo | Valor |
|-------|-------|
| Código de contexto | `SA` |
| Tipo | Determinístico — gobernado por SDD (la constitución lista "seguridad" explícitamente entre los componentes determinísticos, P-06) |
| Estado | Borrador — Etapa 0 |
| Principios rectores | P-06, P-08, P-10 |

---

## 1. Propósito y frontera del contexto

Seguridad y Acceso autentica identidades, gestiona roles y permisos, y decide —para
cada solicitud de cualquier otro contexto— si una identidad puede ejecutar una
acción sobre un recurso. Es la dependencia que **todos** los demás contextos ya
asumen: `specs/spec-infra-servicios.md` §7 deja explícito que, hasta que este
contexto exista, los servicios HTTP de captura-ingesta y records-custodia no deben
exponerse fuera de una red de confianza; `specs/005-indexacion-busqueda/spec.md`
exige un filtrado de permisos con tolerancia cero (RF-IB-008); todo contexto
determinístico ya especificado declara "Seguridad y Acceso" como destino de sus
eventos de auditoría de dominio.

No es un contexto probabilístico: la decisión de "¿puede este actor hacer esto?" es
una regla determinística contra roles, permisos y niveles de clasificación de la
información — no una inferencia.

**Dentro de la frontera:** autenticación de identidades, gestión de roles y
permisos, decisión de autorización por acción y recurso, clasificación de la
información (pública / clasificada / reservada) y su efecto en el acceso, registro
de eventos de seguridad, exposición de permisos a otros contextos.

**Fuera de la frontera (de este contexto):** la bitácora de auditoría de dominio de
cada contexto (p. ej. `BitacoraAuditoria` de Records/Custodia, ya implementada
— RF-RC-005) sigue siendo responsabilidad de ese contexto; Seguridad y Acceso
recibe esos eventos para monitoreo de seguridad, no los sustituye (ver §8). La
interfaz de revisión humana en sí (contexto Validación Humana) consume permisos de
este contexto, pero no los define.

---

## 2. Lenguaje ubicuo

- **Actor** — un usuario humano o un actor de sistema (servicio, componente) que
  interactúa con el producto; el mismo concepto que ya aparece como `actor` en
  todo `EventoAuditoria` de los contextos ya implementados.
- **Identidad** — la representación autenticada de un actor.
- **Rol** — conjunto nombrado de permisos que se asigna a una identidad.
- **Permiso** — la autorización concreta para una acción sobre un tipo de recurso
  (documento, expediente, serie, endpoint).
- **Nivel de clasificación de la información** — pública, clasificada o reservada
  (Ley 1712 de 2014), asignado a un documento o expediente; determina quién puede
  verlo.
- **Decisión de autorización** — el resultado (permitido / denegado) de evaluar si
  una identidad puede ejecutar una acción sobre un recurso.
- **Evento de seguridad** — un evento de autenticación, autorización o denegación
  de acceso; distinto del evento de auditoría de dominio que cada contexto ya
  genera (P-08), aunque relacionado con él.
- **Sesión** — el contexto de una identidad autenticada activa, con expiración.

---

## 3. Modelo de dominio

### Agregados y entidades

- **Identidad** (agregado raíz) — actor, referencia a sus credenciales (nunca el
  secreto en sí), roles asignados, estado (activa / suspendida).
- **Rol** — nombre, conjunto de permisos.
- **Permiso** — acción, tipo de recurso, condición (p. ej. nivel de clasificación
  máximo que cubre).
- **Decisión de autorización** — identidad, recurso solicitado, acción, resultado,
  la regla que la produjo, fecha.
- **Evento de seguridad** — actor, tipo (autenticación exitosa / fallida,
  autorización denegada), fecha, recurso.

### Invariantes (no negociables)

1. Ninguna operación sobre un documento, expediente o endpoint del sistema se
   ejecuta sin una decisión de autorización explícita — no hay acceso por defecto
   (denegar por defecto).
2. Toda decisión de autorización, permitida o denegada, es atribuible a una
   identidad y queda registrada como evento de seguridad (mismo espíritu de P-08,
   aplicado al acceso).
3. Un nivel de clasificación de la información restringe estrictamente quién puede
   ver ese recurso; una identidad sin el permiso correspondiente nunca lo recibe,
   ni siquiera como referencia (mismo gate duro que `RF-IB-008` ya asume desde el
   lado consumidor).
4. Las credenciales nunca se almacenan en texto plano ni se exponen en un evento
   de auditoría o de seguridad.
5. La revocación de un rol o permiso surte efecto de inmediato sobre solicitudes
   nuevas; una identidad revocada no conserva acceso indefinidamente a través de
   una sesión ya emitida.

---

## 4. Contrato del contexto

### Entradas (inbound)

| Origen | Mensaje |
|--------|---------|
| Cualquier otro contexto | Solicitud de autorización para una acción sobre un recurso |
| Cualquier otro contexto | Eventos de auditoría de dominio, para monitoreo de seguridad (no como bitácora sustituta — ver §8) |
| Administrador | Alta / baja de identidades; asignación de roles y permisos |
| Records/Custodia o Enriquecimiento | Nivel de clasificación de la información de un documento o expediente (mecanismo exacto `[CLARIFICAR]`, ver §8) |

### Salidas (outbound)

| Destino | Mensaje |
|---------|---------|
| Cualquier otro contexto | Decisión de autorización (permitido / denegado) para cada solicitud |
| Indexación y Búsqueda | Permisos del usuario consultante (RF-IB, entrada ya declarada en esa spec) |
| Operador / Auditor | Reporte de eventos de seguridad (intentos fallidos, denegaciones, anomalías) |

---

## 5. Requisitos funcionales

> Estado de cada requisito: `Borrador`. Criterios en formato Dado / Cuando / Entonces.

**RF-SA-001 · Autenticación de identidades**
El contexto verifica que un actor es quien dice ser antes de permitir cualquier
operación.
- Dado un actor que se presenta con credenciales, Cuando se autentica, Entonces
  obtiene una identidad válida solo si las credenciales son correctas.

**RF-SA-002 · Gestión de roles y permisos**
Los roles y permisos se dan de alta, se modifican y se retiran sin cambios de
código.
- Dado un nuevo rol con un conjunto de permisos, Cuando se configura, Entonces
  queda disponible para asignarse a identidades sin desplegar código nuevo.

**RF-SA-003 · Autorización por defecto denegada**
Toda solicitud de acción sobre un recurso requiere una decisión explícita de
autorización; sin ella, se deniega.
- Dada una solicitud de acción sobre un recurso sin una regla de autorización que
  la permita explícitamente, Cuando se evalúa, Entonces se deniega.

**RF-SA-004 · Clasificación de la información y su efecto en el acceso**
Un documento o expediente con nivel clasificado o reservado solo es accesible para
identidades con el permiso correspondiente a ese nivel.
- Dado un recurso con nivel de clasificación reservado, Cuando una identidad sin el
  permiso correspondiente lo solicita, Entonces la solicitud se deniega.

**RF-SA-005 · Registro de eventos de seguridad**
Toda decisión de autorización y todo intento de autenticación, exitoso o no, queda
registrado, atribuible y fechado.
- Dado un intento de autenticación o una decisión de autorización, Cuando ocurre,
  Entonces existe un evento de seguridad con actor, fecha, tipo y resultado.

**RF-SA-006 · Revocación efectiva e inmediata**
Revocar un rol o permiso de una identidad impide accesos nuevos de inmediato.
- Dada una identidad con un permiso revocado, Cuando solicita una acción que
  dependía de ese permiso, Entonces se deniega desde la revocación en adelante.

**RF-SA-007 · Protección de credenciales**
Ninguna credencial se almacena en texto plano ni se expone en un evento de
auditoría o de seguridad.
- Dado un evento de seguridad o de auditoría, Cuando se consulta, Entonces no
  contiene ninguna credencial en texto plano.

**RF-SA-008 · Exposición de permisos a otros contextos**
Cualquier contexto puede consultar los permisos de una identidad sobre un recurso
para aplicar su propio filtrado.
- Dada una identidad y un recurso, Cuando otro contexto consulta los permisos,
  Entonces recibe una respuesta que le permite filtrar sin duplicar la lógica de
  autorización.

**RF-SA-009 · Operación sin conectividad saliente**
La autenticación y la autorización operan completas en modo on-premise aislado, sin
depender obligatoriamente de un proveedor de identidad externo.
- Dado un despliegue on-premise sin conectividad saliente, Cuando se autentica o se
  autoriza un actor, Entonces la operación se completa sin salir de la red del
  cliente.

**RF-SA-010 · Cero pérdida silenciosa de eventos de seguridad**
Todo intento de autenticación o autorización, exitoso o no, genera un evento; nunca
se descarta en silencio.
- Dado un conjunto de intentos de autenticación y autorización, Cuando se audita,
  Entonces cada uno tiene un evento de seguridad correspondiente.

---

## 6. Requisitos no funcionales

**RNF-SA-001 · Rendimiento en el camino crítico** — la verificación de autorización
no degrada perceptiblemente la latencia de las operaciones de los demás contextos,
que la invocan en cada solicitud.

**RNF-SA-002 · Paridad de despliegue** — toda la funcionalidad opera idéntica en
SaaS y on-premise, incluyendo entornos sin conectividad saliente (P-02, P-10).

**RNF-SA-003 · Resistencia a manipulación de la bitácora de seguridad** — el
almacenamiento de eventos de seguridad es de solo anexado y permite detectar
manipulación, mismo tratamiento que la bitácora de auditoría de dominio
(RNF-RC-002).

**RNF-SA-004 · Mínimo privilegio por defecto** — un rol nuevo no otorga permisos
amplios por defecto; cada permiso se otorga explícitamente.

---

## 7. Trazabilidad regulatoria

> La columna *Referencia específica* queda **PENDIENTE** de fijar contra el documento
> oficial por el archivista del design partner. No se inventan números de cláusula.
> Donde el requisito nace de un principio de la constitución y no de una fuente
> externa, la columna es `N/A`.

| Requisito | Fuente normativa | Referencia específica | Validado |
|-----------|------------------|-----------------------|----------|
| RF-SA-001 | Ley 1712 de 2014 (transparencia y acceso a la información pública); Requisitos funcionales de SGDEA (AGN) | PENDIENTE | ☐ |
| RF-SA-002 | Requisitos funcionales de SGDEA (AGN) | PENDIENTE | ☐ |
| RF-SA-003 | Constitución del proyecto — integridad (El objeto a proteger) | N/A | ☐ |
| RF-SA-004 | Ley 1712 de 2014 (información pública clasificada y reservada) | PENDIENTE | ☐ |
| RF-SA-005 | Constitución del proyecto P-08 | N/A | ☐ |
| RF-SA-006 | Constitución del proyecto — integridad (El objeto a proteger) | N/A | ☐ |
| RF-SA-007 | Ley 1581 de 2012 (protección de datos personales) | PENDIENTE | ☐ |
| RF-SA-008 | Constitución del proyecto P-03 (interfaz consumida por otros contextos) | N/A | ☐ |
| RF-SA-009 | Constitución del proyecto P-10 | N/A | ☐ |
| RF-SA-010 | Constitución del proyecto P-08; Ley 594 de 2000 (integridad del acervo) | PENDIENTE | ☐ |

---

## 8. Decisiones pendientes / preguntas abiertas

- **[CLARIFICAR]** Dónde y cómo se captura el nivel de clasificación de la
  información (pública / clasificada / reservada, Ley 1712 de 2014) de un
  documento o expediente: ¿un campo más del esquema de metadatos obligatorios de
  Enriquecimiento (ya `[CLARIFICAR]` en `specs/004-enriquecimiento/spec.md` §8),
  una decisión humana separada, o un atributo que este contexto gestiona de forma
  independiente?
- **[CLARIFICAR]** Modelo de roles y permisos concreto: basado en rol (RBAC), en
  atributos (ABAC — p. ej. dependencia organizacional + nivel de clasificación), o
  un híbrido. No se fija sin dato real del design partner.
- **[CLARIFICAR]** Proveedor de identidad: si el sistema implementa su propio
  almacén de identidades o se integra con uno externo (LDAP/AD, SSO), y en ese
  caso cómo se sostiene RF-SA-009 (operación sin conectividad saliente) si el
  proveedor elegido es externo.
- **Relación sin resolver entre "eventos de seguridad" y "eventos de auditoría de
  dominio":** cada contexto ya especificado (Captura/Ingesta, Normalización,
  Extracción) declara "Seguridad y Acceso" como destino de sus eventos de
  auditoría, y Records/Custodia ya tiene su propia `BitacoraAuditoria` implementada
  (RF-RC-005, T-08..T-21). Esta spec asume que Seguridad y Acceso **recibe** esos
  eventos para monitoreo, sin sustituir la bitácora de cada contexto como fuente de
  verdad — pero ninguna spec anterior lo dice explícitamente y ninguna implementa
  hoy el envío real de esos eventos hacia un destino externo (siguen solo en la
  bitácora local de cada contexto). Queda como brecha a cerrar, no inventada aquí.
