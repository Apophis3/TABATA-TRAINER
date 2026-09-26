# [NOMBRE_DEL_PROYECTO] - ANCLA DE ESPECIFICACIÓN (PROJECT DNA)

## 1. Visión General y Propósito del Software
- **Problema que Resuelve**: [Detallar explícitamente qué problema de negocio resuelve este software]
- **Objetivo Principal**: [Cuál es la meta última del sistema]
- **Criterios de Éxito de Negocio**:
  - [Métrica 1: ej. Tiempo de respuesta < 200ms]
  - [Métrica 2: ej. Soporte multi-usuario concurrente de 10K]
  - [Métrica 3: ej. Sincronización de inventario < 5s]

## 2. Stack Tecnológico Estricto y Convenciones de Código
- **Lenguajes de Programación**: [Definir lenguaje y versión exacta, ej. TypeScript v5.x, Python v3.12]
- **Frameworks y Librerías**: [ej. NestJS, React v19, Tailwind CSS, Prisma ORM]
- **Arquitectura Exigida**: [Clean Architecture / Vertical Slice / Arquitectura de Cebolla. Justificar la modularidad]
- **Prohibiciones Críticas (Do Not)**:
  - NO realizar ediciones de código manuales directas; si hay un error, corregir la especificación y regenerar.
  - NO mezclar lógica de negocio con lógica de infraestructura (ej. controladores hablando directo con SQL).
  - NO omitir las firmas de tipado estricto (no utilizar 'any' en TypeScript).
  - NO dejar credenciales ni variables de entorno hardcoded en los archivos de código.

## 3. Topología de Orquestación Multi-Agente (Graph Engineering)
- **Roles y Mandatos de Nodos Heterogéneos**:
  - **Planificador (Plan Mode)**: [ej. Claude 3.7 / o1 / DeepSeek R1] -> Encargado exclusivo de revisar la arquitectura y estructurar la lista de tareas detallada.
  - **Ejecutores (Act Mode)**: [ej. Modelos rápidos de bajo coste] -> Encargados de implementar los cambios físicos en los archivos basados exclusivamente en el plan aprobado.
  - **Verificadores (Auditor / Juez)**: [ej. Instancias independientes con contexto fresco o modelo distinto] -> Encargados de desafiar el código generado y evaluar riesgos antes del merge.
- **Control de Presupuesto y Límites de Ejecución**:
  - Parada automática del bucle de la tarea ante fallos repetidos (máximo 3 intentos por el mismo error).
  - Límite de tokens por sesión del agente para prevenir el consumo descontrolado del presupuesto.
- **Aislamiento Físico de Agentes**:
  - **Uso Obligatorio de Git Worktrees**: Cada agente o sub-agente especializado operará en un directorio físico independiente y una rama aislada para evitar colisiones y mezclas corruptas de contextos de trabajo en paralelo.

## 4. Gestión de Contexto, Memoria y Conexiones de Verdad
- **Protocolo MCP (Model Context Protocol)**:
  - Servidores MCP autorizados: [ej. Context 7 para documentaciones actualizadas, Supabase/Prisma para esquemas de base de datos remotos, Notion/Jira para historias de usuario].
  - *Regla*: La IA debe consultar la información mediante MCP en tiempo real bajo demanda en lugar de saturar la ventana de contexto.
- **Estructuración de Skills (Lazy Loading)**:
  - Configuración del archivo de entrada principal `agent.md` como índice dinámico. Las directrices complejas se dividen en archivos de habilidad pequeños que la IA solo cargará cuando la tarea lo requiera.
- **Persistencia mediante Cerebro del Proyecto (Engram)**:
  - Registro sistemático de las decisiones arquitectónicas (el "porqué" y no solo el "qué") y el aprendizaje derivado de la corrección de errores críticos en `engram.md`. Esto actúa como memoria permanente a largo plazo para evitar la amnesia agéntica en futuras sesiones.

## 5. El Flujo de Trabajo "Diamante" (Diamond Pattern)
1. **Divergencia / Planificación**: El nodo planificador evalúa la tarea y genera el documento de planos (`tasks.md`) detallando la estrategia de ataque. Requiere aprobación humana (Human-in-the-loop) para proceder.
2. **Ejecución Paralela**: Los sub-agentes efímeros se despliegan de forma concurrente en sus respectivos Git Worktrees para abordar las tareas atómicas del plan.
3. **Verificación Independiente**: Jueces deterministas (ejecución de tests automáticos, consultas reales a base de datos) y auditores probabilísticos desafían el código de los ejecutores.
4. **Convergencia / Fusión (Merge)**: Integración segura del código modificado una vez superada la fase de veto.

## 6. Control de Calidad y Pipeline de Producción (CI/CD)
- **Definición de Terminado (Definition of Done)**:
  - El código debe compilar correctamente en un entorno limpio sin warnings.
  - Cobertura de tests unitarios y de integración > 90%.
  - Validación de esquemas y tipos de datos de bases de datos.
- **Revisiones de Pull Request Automatizadas**:
  - Integración de herramientas de auditoría en la PR (ej. Code Rabbit) para verificar vulnerabilidades de seguridad de nivel OWASP y credenciales expuestas antes del merge.
- **Automatización de Lanzamientos (Releases)**:
  - Uso estricto de convenciones de commits (Semantic Commits) combinados con Release Please para la generación automática de versionado semántico y notas de lanzamiento (AI-Release Notes).
