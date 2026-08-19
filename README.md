# SGDEA — Capa AI-native de clasificación e indexación documental

Estado: **Etapa 0 — andamiaje**. Este repositorio aún no tiene lógica de negocio;
contiene la estructura, los contratos y los esqueletos sobre los que se construye.

La fuente de verdad del producto es [`specs/`](specs/README.md) (Spec-Driven
Development). El código se escribe **contra** esas specs, no antes que ellas.

---

## Stack

Monorepo poliglota, un solo código base para los dos modos de despliegue (P-02):

| Capa | Lenguaje | Framework | Build |
|------|----------|-----------|-------|
| Núcleo determinístico (SDD) | Kotlin | Spring Boot | Gradle |
| Capa probabilística + arnés EDD | Python 3.12 | FastAPI | uv |

La frontera entre ambas capas es **física** (P-01): los contextos probabilísticos
nunca escriben estado directamente sobre `records-custodia`; solo emiten
`Sugerencia` a través de su capa anticorrupción.

Empaquetado: Docker, con `deploy/docker-compose.saas.yml` y
`deploy/docker-compose.onprem.yml` — mismo código base, distinta implementación
detrás de cada interfaz de P-03 según el modo. CI: GitHub Actions
(`.github/workflows/ci.yml`).

---

## Estructura

```
sgdea/
├── specs/                          # fuente de verdad (SDD/EDD)
├── contexts/
│   ├── captura-ingesta/            # Kotlin · SDD
│   ├── normalizacion/              # Python · EDD (límites) + determinístico (dedup)
│   ├── extraccion/                 # Python · EDD (OCR)
│   ├── clasificacion/              # Python · EDD
│   ├── enriquecimiento/            # Python · EDD (metadatos)
│   ├── indexacion-busqueda/        # Python · EDD (recuperación/Q&A) + plumbing de índices
│   ├── records-custodia/           # Kotlin · SDD · núcleo — incluye la capa anticorrupción (P-01)
│   ├── seguridad-acceso/           # Kotlin · SDD
│   └── validacion-humana/          # Kotlin · SDD · producto de primera clase (P-09)
├── platform-kotlin/                # P-03 lado JVM (interfaces + impl SaaS/self-hosted)
├── platform-python/                # P-03 lado Python (interfaces + impl SaaS/self-hosted)
├── eval-harness/                   # arnés EDD ejecutable (ver specs/eval/edd-harness.md)
├── deploy/                         # docker-compose por modo de despliegue
└── .github/workflows/              # CI
```

Solo `records-custodia` y `captura-ingesta` se desarrollarán en el corto plazo;
el resto de los contextos quedan como esqueletos vacíos hasta que les toque turno.

---

## Arrancar

**Kotlin / Gradle**

```
./gradlew build
```

**Python / uv** (workspace con los siete miembros bajo `contexts/`,
`platform-python/` y `eval-harness/`)

```
uv sync --all-packages
```

Ambos comandos deben correr en verde sobre un repo recién clonado; hoy no
compilan ni ejecutan lógica real, solo la estructura.
