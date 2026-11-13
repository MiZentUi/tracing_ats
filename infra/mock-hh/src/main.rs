use axum::{
    extract::{Multipart, Path, Query, State},
    http::StatusCode,
    response::{Html, Redirect},
    routing::{delete, get, post, put},
    Json, Router,
};
use chrono::Utc;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::{collections::HashMap, env, net::SocketAddr, path::PathBuf, sync::Arc};
use tokio::sync::RwLock;
use tower_http::trace::TraceLayer;
use tracing::{info, warn};
use uuid::Uuid;

#[derive(Clone)]
struct AppState {
    mock: Arc<RwLock<MockState>>,
    webhook_url: String,
    public_url: String,
    employer_id: String,
    state_file: Option<PathBuf>,
    http: reqwest::Client,
}

#[derive(Default, Serialize, Deserialize)]
struct MockState {
    next_id: u64,
    drafts: HashMap<String, Draft>,
    vacancies: HashMap<String, Vacancy>,
    resumes: HashMap<String, Value>,
    subscriptions: HashMap<String, String>,
    #[serde(default)]
    responses: HashMap<String, Vec<ResponseRecord>>,
}

#[derive(Clone, Serialize, Deserialize)]
struct ResponseRecord {
    resume_id: String,
    event_id: String,
    topic_id: String,
    responded_at: String,
    status: u16,
}

#[derive(Clone, Serialize, Deserialize)]
struct Draft {
    name: String,
    description: String,
}

#[derive(Clone, Serialize, Deserialize)]
struct Vacancy {
    id: String,
    name: String,
    description: String,
    archived: bool,
}

#[derive(Deserialize)]
struct RespondRequest {
    vacancy_id: String,
    resume_id: String,
    event_id: Option<String>,
    topic_id: Option<String>,
    action_type: Option<String>,
}

#[derive(Serialize)]
struct RespondResponse {
    event_id: String,
    topic_id: String,
    target_status: u16,
}

#[tokio::main]
async fn main() {
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "mock_hh=info,tower_http=info".into()),
        )
        .init();

    let state_file = env::var("MOCK_HH_STATE_FILE")
        .ok()
        .filter(|value| !value.is_empty())
        .map(PathBuf::from);
    let loaded_state = state_file
        .as_ref()
        .and_then(|path| std::fs::read_to_string(path).ok())
        .and_then(|content| serde_json::from_str::<MockState>(&content).ok())
        .unwrap_or_else(seed_state);

    let state = AppState {
        mock: Arc::new(RwLock::new(loaded_state)),
        webhook_url: env::var("MOCK_HH_WEBHOOK_URL")
            .or_else(|_| env::var("ATS_HH_WEBHOOK_URL"))
            .unwrap_or_else(|_| {
                "http://localhost:8080/api/v1/integrations/hh/webhook?token=mock-hh-token".into()
            }),
        public_url: env::var("MOCK_HH_PUBLIC_URL")
            .unwrap_or_else(|_| "http://localhost:8090".into()),
        employer_id: env::var("MOCK_HH_EMPLOYER_ID").unwrap_or_else(|_| "999".into()),
        state_file,
        http: reqwest::Client::new(),
    };

    let app = Router::new()
        .route("/", get(index_page))
        .route("/vacancy/{vacancy_id}", get(vacancy_page))
        .route("/vacancy/{vacancy_id}/respond", post(respond_page))
        .route("/health", get(health))
        .route("/vacancies/drafts", post(create_draft))
        .route(
            "/vacancies/drafts/{draft_id}",
            get(get_draft).put(update_draft),
        )
        .route("/vacancies/drafts/{draft_id}/publish", post(publish_draft))
        .route("/vacancies/{vacancy_id}", get(get_vacancy))
        .route("/resumes/{resume_id}", get(get_resume))
        .route(
            "/webhook/subscriptions",
            get(list_subscriptions).post(create_subscription),
        )
        .route(
            "/webhook/subscriptions/{subscription_id}",
            delete(delete_subscription),
        )
        .route(
            "/employers/{employer_id}/vacancies/archived/{vacancy_id}",
            put(archive_vacancy),
        )
        .route("/__admin/respond", post(admin_respond))
        .route("/__admin/reset", post(admin_reset))
        .route("/__admin/state", get(admin_state))
        .layer(TraceLayer::new_for_http())
        .with_state(state);

    let port: u16 = env::var("MOCK_HH_PORT")
        .ok()
        .and_then(|value| value.parse().ok())
        .unwrap_or(8090);
    let address = SocketAddr::from(([0, 0, 0, 0], port));
    info!("mock_hh_started address={}", address);
    let listener = tokio::net::TcpListener::bind(address)
        .await
        .expect("bind mock-hh");
    axum::serve(listener, app).await.expect("serve mock-hh");
}

async fn health() -> Json<Value> {
    Json(json!({"status": "ok"}))
}

async fn index_page(State(state): State<AppState>) -> Html<String> {
    let mock = state.mock.read().await;
    let mut vacancies: Vec<&Vacancy> = mock.vacancies.values().collect();
    vacancies.sort_by(|left, right| right.id.cmp(&left.id));

    let content = if vacancies.is_empty() {
        "<div class=\"empty\">Пока нет опубликованных вакансий. Опубликуйте вакансию из Tracing ATS — она появится здесь.</div>".to_string()
    } else {
        vacancies
            .iter()
            .map(|vacancy| {
                let responses = mock.responses.get(&vacancy.id).map(Vec::len).unwrap_or(0);
                format!(
                    "<a class=\"card\" href=\"/vacancy/{id}\">\
                     <div class=\"card-head\"><strong>{name}</strong><span class=\"badge\">{status}</span></div>\
                     <div class=\"muted\">hh vacancy id: {id} · откликов: {responses}</div>\
                     </a>",
                    id = html_escape(&vacancy.id),
                    name = html_escape(&vacancy.name),
                    responses = responses,
                    status = if vacancy.archived { "в архиве" } else { "опубликована" }
                )
            })
            .collect::<String>()
    };

    Html(format!(
        "<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\">\
         <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\
         <title>mock hh.ru — вакансии</title>{style}</head><body>\
         {header}\
         <main><h1>Опубликованные вакансии</h1>\
         <p class=\"muted\">Это локальная заглушка hh.ru. Здесь можно открыть вакансию и отправить отклик в Tracing ATS.</p>\
         <div class=\"grid\">{content}</div></main></body></html>",
        style = PAGE_STYLE,
        header = PAGE_HEADER,
        content = content
    ))
}

async fn vacancy_page(
    State(state): State<AppState>,
    Path(vacancy_id): Path<String>,
    Query(query): Query<HashMap<String, String>>,
) -> Result<Html<String>, (StatusCode, Html<String>)> {
    let mock = state.mock.read().await;
    let vacancy = mock.vacancies.get(&vacancy_id).cloned().ok_or_else(|| {
        (
            StatusCode::NOT_FOUND,
            Html(error_page(
                "Вакансия не найдена",
                "В mock-hh нет такой вакансии.",
            )),
        )
    })?;

    let responded = query.contains_key("responded");
    let banner = if responded {
        "<div class=\"success\">Отклик отправлен в Tracing ATS. Вернитесь в ATS и обновите карточку вакансии.</div>"
    } else {
        ""
    };
    let respond_block = if vacancy.archived {
        "<div class=\"empty\">Вакансия в архиве, откликнуться нельзя.</div>".to_string()
    } else {
        format!(
            "<form class=\"respond\" method=\"post\" action=\"/vacancy/{id}/respond\" enctype=\"multipart/form-data\">\
             <label for=\"resume\">Резюме</label>\
             <select id=\"resume\" name=\"resume_id\">\
             <option value=\"resume-strong\">Сильное: Java, Spring Boot, PostgreSQL, Kafka, Docker</option>\
             <option value=\"resume-weak\">Слабое: Python без опыта</option>\
             <option value=\"resume-empty\">Пустое резюме</option>\
             <option value=\"custom\">Своё резюме: текст или файл</option>\
             </select>\
             <div class=\"custom\">\
             <label for=\"resume-text\">Текст резюме — заполните, если выбрали «Своё резюме»</label>\
             <textarea id=\"resume-text\" name=\"resume_text\" rows=\"8\" placeholder=\"Вставьте текст резюме: опыт, навыки, образование...\"></textarea>\
             <label for=\"resume-file\">Или файл .txt / .md</label>\
             <input id=\"resume-file\" type=\"file\" name=\"resume_file\" accept=\".txt,.md,text/plain,text/markdown\">\
             </div>\
             <button type=\"submit\">Отправить отклик</button>\
             </form>",
            id = html_escape(&vacancy.id)
        )
    };

    let responses = mock.responses.get(&vacancy.id).cloned().unwrap_or_default();
    let responses_block = if responses.is_empty() {
        String::new()
    } else {
        let rows = responses
            .iter()
            .rev()
            .map(|response| {
                format!(
                    "<li><strong>{resume}</strong><div class=\"muted\">event_id: {event} · topic_id: {topic} · {time}</div></li>",
                    resume = html_escape(&resume_label(&response.resume_id)),
                    event = html_escape(&response.event_id),
                    topic = html_escape(&response.topic_id),
                    time = html_escape(&response.responded_at)
                )
            })
            .collect::<String>();
        format!(
            "<section class=\"responses\"><h2>Отклики в Tracing ATS</h2><ul>{rows}</ul></section>"
        )
    };

    Ok(Html(format!(
        "<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\">\
         <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\
         <title>{name} — mock hh.ru</title>{style}</head><body>\
         {header}\
         <main>\
         <a class=\"back\" href=\"/\">← Все вакансии</a>\
         <h1>{name}</h1>\
         <div class=\"meta\">Работодатель: {employer} · hh vacancy id: {id} · {status}</div>\
         {banner}\
         <section class=\"description\">{description}</section>\
         {respond_block}\
         {responses_block}\
         </main></body></html>",
        name = html_escape(&vacancy.name),
        employer = html_escape(&state.employer_id),
        id = html_escape(&vacancy.id),
        status = if vacancy.archived {
            "в архиве"
        } else {
            "опубликована"
        },
        description = html_escape(&vacancy.description),
        banner = banner,
        respond_block = respond_block,
        responses_block = responses_block,
        style = PAGE_STYLE,
        header = PAGE_HEADER
    )))
}

async fn respond_page(
    State(state): State<AppState>,
    Path(vacancy_id): Path<String>,
    mut multipart: Multipart,
) -> Result<Redirect, (StatusCode, Html<String>)> {
    let mut resume_id = String::new();
    let mut resume_text = String::new();
    let mut file_text: Option<String> = None;

    while let Some(field) = multipart.next_field().await.map_err(|error| {
        (
            StatusCode::BAD_REQUEST,
            Html(error_page("Некорректная форма", &error.to_string())),
        )
    })? {
        let name = field.name().unwrap_or("").to_string();
        match name.as_str() {
            "resume_id" => resume_id = field.text().await.unwrap_or_default(),
            "resume_text" => resume_text = field.text().await.unwrap_or_default(),
            "resume_file" => {
                let filename = field.file_name().unwrap_or("").to_lowercase();
                let bytes = field.bytes().await.map_err(|error| {
                    (
                        StatusCode::BAD_REQUEST,
                        Html(error_page("Не удалось прочитать файл", &error.to_string())),
                    )
                })?;
                if bytes.is_empty() {
                    continue;
                }
                if filename.ends_with(".pdf") || filename.ends_with(".docx") {
                    return Err((
                        StatusCode::UNPROCESSABLE_ENTITY,
                        Html(error_page(
                            "Формат файла не поддержан",
                            "Mock hh.ru принимает TXT и MD. PDF/DOCX загрузите через раздел «Резюме» в Tracing ATS.",
                        )),
                    ));
                }
                match String::from_utf8(bytes.to_vec()) {
                    Ok(text) => file_text = Some(text),
                    Err(_) => {
                        return Err((
                            StatusCode::UNPROCESSABLE_ENTITY,
                            Html(error_page(
                                "Файл не похож на текст",
                                "Загрузите TXT или MD в кодировке UTF-8.",
                            )),
                        ))
                    }
                }
            }
            _ => {}
        }
    }

    let selected_resume_id = if resume_id == "custom" {
        let text = if !resume_text.trim().is_empty() {
            resume_text
        } else {
            file_text.unwrap_or_default()
        };
        if text.trim().is_empty() {
            return Err((
                StatusCode::UNPROCESSABLE_ENTITY,
                Html(error_page(
                    "Нет текста резюме",
                    "Вставьте текст резюме или загрузите TXT/MD файл.",
                )),
            ));
        }
        let id = format!("resume-custom-{}", Uuid::new_v4());
        let resume = json!({
            "id": id,
            "title": "Резюме кандидата",
            "skills": text,
            "skill_set": [],
            "experience": [],
            "education": {}
        });
        {
            let mut mock = state.mock.write().await;
            mock.resumes.insert(id.clone(), resume);
            persist(&state, &mock);
        }
        info!("mock_hh_custom_resume_created resume_id={}", id);
        id
    } else if resume_id.is_empty() {
        "resume-strong".to_string()
    } else {
        resume_id
    };

    match deliver_webhook(&state, &vacancy_id, &selected_resume_id, None, None, None).await {
        Ok(_) => Ok(Redirect::to(&format!("/vacancy/{vacancy_id}?responded=1"))),
        Err((status, message)) => Err((
            status,
            Html(error_page(
                "Не удалось отправить отклик",
                &format!("{message}. Проверьте, что Tracing ATS запущен."),
            )),
        )),
    }
}

async fn deliver_webhook(
    state: &AppState,
    vacancy_id: &str,
    resume_id: &str,
    event_id: Option<String>,
    topic_id: Option<String>,
    action_type: Option<String>,
) -> Result<(String, String, u16), (StatusCode, String)> {
    let subscription_id = {
        let mock = state.mock.read().await;
        mock.subscriptions
            .keys()
            .next()
            .cloned()
            .unwrap_or_else(|| "sub-default".to_string())
    };
    let event_id = event_id.unwrap_or_else(|| format!("event-{}", Uuid::new_v4()));
    let topic_id = topic_id.unwrap_or_else(|| format!("topic-{}", Uuid::new_v4()));
    let action_type = action_type.unwrap_or_else(|| "NEW_NEGOTIATION_VACANCY".to_string());
    let payload = json!({
        "id": event_id,
        "subscription_id": subscription_id,
        "action_type": action_type,
        "user_id": "0",
        "payload": {
            "chat_id": format!("chat-{}", Uuid::new_v4()),
            "topic_id": topic_id,
            "resume_id": resume_id,
            "vacancy_id": vacancy_id,
            "employer_id": state.employer_id,
            "negotiation_date": Utc::now().to_rfc3339()
        }
    });

    info!(
        "mock_hh_webhook_sending event_id={} hh_vacancy_id={} hh_resume_id={}",
        event_id, vacancy_id, resume_id
    );
    let response = state
        .http
        .post(&state.webhook_url)
        .json(&payload)
        .send()
        .await
        .map_err(|error| {
            warn!("mock_hh_webhook_failed error={}", error);
            (
                StatusCode::BAD_GATEWAY,
                "webhook_delivery_failed".to_string(),
            )
        })?;
    let status = response.status().as_u16();
    info!(
        "mock_hh_webhook_sent event_id={} status={}",
        event_id, status
    );
    if !response.status().is_success() {
        return Err((
            StatusCode::BAD_GATEWAY,
            format!("webhook_rejected status={status}"),
        ));
    }
    {
        let mut mock = state.mock.write().await;
        mock.responses
            .entry(vacancy_id.to_string())
            .or_default()
            .push(ResponseRecord {
                resume_id: resume_id.to_string(),
                event_id: event_id.clone(),
                topic_id: topic_id.clone(),
                responded_at: Utc::now().to_rfc3339(),
                status,
            });
        persist(state, &mock);
    }
    Ok((event_id, topic_id, status))
}

async fn create_draft(State(state): State<AppState>, Json(body): Json<Value>) -> Json<Value> {
    let mut mock = state.mock.write().await;
    mock.next_id += 1;
    let id = mock.next_id.to_string();
    let draft = Draft {
        name: body
            .get("name")
            .and_then(Value::as_str)
            .unwrap_or("Вакансия")
            .to_string(),
        description: body
            .get("description")
            .and_then(Value::as_str)
            .unwrap_or("")
            .to_string(),
    };
    let name = draft.name.clone();
    mock.drafts.insert(id.clone(), draft);
    info!("mock_hh_draft_created hh_draft_id={}", id);
    persist(&state, &mock);
    Json(json!({
        "id": id,
        "name": name,
        "publication_ready": true,
        "ignored_fields": [],
        "validation_errors": []
    }))
}

async fn get_draft(
    State(state): State<AppState>,
    Path(draft_id): Path<String>,
) -> Result<Json<Value>, (StatusCode, Json<Value>)> {
    let mock = state.mock.read().await;
    let draft = mock.drafts.get(&draft_id).cloned().ok_or_else(|| {
        (
            StatusCode::NOT_FOUND,
            Json(json!({"errors": [{"type": "not_found"}]})),
        )
    })?;
    Ok(Json(json!({
        "id": draft_id,
        "name": draft.name,
        "publication_ready": true,
        "ignored_fields": [],
        "validation_errors": []
    })))
}

async fn update_draft(
    State(state): State<AppState>,
    Path(draft_id): Path<String>,
    Json(body): Json<Value>,
) -> Result<Json<Value>, (StatusCode, Json<Value>)> {
    let mut mock = state.mock.write().await;
    let draft = mock.drafts.get_mut(&draft_id).ok_or_else(|| {
        (
            StatusCode::NOT_FOUND,
            Json(json!({"errors": [{"type": "not_found"}]})),
        )
    })?;
    if let Some(name) = body.get("name").and_then(Value::as_str) {
        draft.name = name.to_string();
    }
    if let Some(description) = body.get("description").and_then(Value::as_str) {
        draft.description = description.to_string();
    }
    let name = draft.name.clone();
    info!("mock_hh_draft_updated hh_draft_id={}", draft_id);
    persist(&state, &mock);
    Ok(Json(json!({
        "id": draft_id,
        "name": name,
        "publication_ready": true,
        "ignored_fields": [],
        "validation_errors": []
    })))
}

async fn publish_draft(
    State(state): State<AppState>,
    Path(draft_id): Path<String>,
) -> Result<Json<Value>, (StatusCode, Json<Value>)> {
    let mut mock = state.mock.write().await;
    let draft = mock.drafts.get(&draft_id).cloned().ok_or_else(|| {
        (
            StatusCode::NOT_FOUND,
            Json(json!({"errors": [{"type": "not_found"}]})),
        )
    })?;
    mock.next_id += 1;
    let vacancy_id = (100_000 + mock.next_id).to_string();
    mock.vacancies.insert(
        vacancy_id.clone(),
        Vacancy {
            id: vacancy_id.clone(),
            name: draft.name,
            description: draft.description,
            archived: false,
        },
    );
    info!(
        "mock_hh_vacancy_published hh_draft_id={} hh_vacancy_id={}",
        draft_id, vacancy_id
    );
    persist(&state, &mock);
    Ok(Json(json!({"vacancy_ids": [vacancy_id]})))
}

async fn get_vacancy(
    State(state): State<AppState>,
    Path(vacancy_id): Path<String>,
) -> Result<Json<Value>, (StatusCode, Json<Value>)> {
    let mock = state.mock.read().await;
    let vacancy = mock.vacancies.get(&vacancy_id).ok_or_else(|| {
        (
            StatusCode::NOT_FOUND,
            Json(json!({"errors": [{"type": "not_found"}]})),
        )
    })?;
    Ok(Json(json!({
        "id": vacancy.id,
        "name": vacancy.name,
        "description": vacancy.description,
        "archived": vacancy.archived,
        "alternate_url": format!(
            "{}/vacancy/{}",
            state.public_url.trim_end_matches('/'),
            vacancy.id
        ),
        "employer": {"id": state.employer_id}
    })))
}

async fn get_resume(
    State(state): State<AppState>,
    Path(resume_id): Path<String>,
) -> Result<Json<Value>, (StatusCode, Json<Value>)> {
    if resume_id.contains("forbidden") {
        return Err((
            StatusCode::FORBIDDEN,
            Json(json!({"errors": [{"type": "forbidden"}]})),
        ));
    }
    if resume_id.contains("missing") {
        return Err((
            StatusCode::NOT_FOUND,
            Json(json!({"errors": [{"type": "not_found"}]})),
        ));
    }
    let mock = state.mock.read().await;
    let resume = mock.resumes.get(&resume_id).cloned().ok_or_else(|| {
        (
            StatusCode::NOT_FOUND,
            Json(json!({"errors": [{"type": "not_found"}]})),
        )
    })?;
    info!("mock_hh_resume_served resume_id={}", resume_id);
    Ok(Json(resume))
}

async fn list_subscriptions(State(state): State<AppState>) -> Json<Value> {
    let mock = state.mock.read().await;
    let items: Vec<Value> = mock
        .subscriptions
        .iter()
        .map(|(id, url)| {
            json!({
                "id": id,
                "url": url,
                "actions": [{"type": "NEW_NEGOTIATION_VACANCY", "settings": {"vacancies_only_mine": true}}]
            })
        })
        .collect();
    Json(json!({"items": items}))
}

async fn create_subscription(
    State(state): State<AppState>,
    Json(body): Json<Value>,
) -> Json<Value> {
    let mut mock = state.mock.write().await;
    mock.next_id += 1;
    let id = format!("sub-{}", mock.next_id);
    let url = body
        .get("url")
        .and_then(Value::as_str)
        .unwrap_or("")
        .to_string();
    mock.subscriptions.insert(id.clone(), url.clone());
    info!("mock_hh_subscription_created subscription_id={}", id);
    persist(&state, &mock);
    Json(json!({
        "id": id,
        "url": url,
        "actions": body.get("actions").cloned().unwrap_or_else(|| json!([]))
    }))
}

async fn delete_subscription(
    State(state): State<AppState>,
    Path(subscription_id): Path<String>,
) -> StatusCode {
    let mut mock = state.mock.write().await;
    mock.subscriptions.remove(&subscription_id);
    info!(
        "mock_hh_subscription_deleted subscription_id={}",
        subscription_id
    );
    persist(&state, &mock);
    StatusCode::NO_CONTENT
}

async fn archive_vacancy(
    State(state): State<AppState>,
    Path((_employer_id, vacancy_id)): Path<(String, String)>,
) -> StatusCode {
    let mut mock = state.mock.write().await;
    if let Some(vacancy) = mock.vacancies.get_mut(&vacancy_id) {
        vacancy.archived = true;
        info!("mock_hh_vacancy_archived hh_vacancy_id={}", vacancy_id);
        persist(&state, &mock);
        StatusCode::NO_CONTENT
    } else {
        StatusCode::NOT_FOUND
    }
}

async fn admin_respond(
    State(state): State<AppState>,
    Json(request): Json<RespondRequest>,
) -> Result<(StatusCode, Json<RespondResponse>), (StatusCode, Json<Value>)> {
    match deliver_webhook(
        &state,
        &request.vacancy_id,
        &request.resume_id,
        request.event_id,
        request.topic_id,
        request.action_type,
    )
    .await
    {
        Ok((event_id, topic_id, status)) => Ok((
            StatusCode::ACCEPTED,
            Json(RespondResponse {
                event_id,
                topic_id,
                target_status: status,
            }),
        )),
        Err((status, message)) => Err((status, Json(json!({"error": message})))),
    }
}

async fn admin_reset(State(state): State<AppState>) -> Json<Value> {
    let mut mock = state.mock.write().await;
    *mock = seed_state();
    info!("mock_hh_state_reset");
    persist(&state, &mock);
    Json(json!({"status": "reset"}))
}

async fn admin_state(State(state): State<AppState>) -> Json<Value> {
    let mock = state.mock.read().await;
    Json(json!({
        "drafts": mock.drafts.len(),
        "vacancies": mock.vacancies.len(),
        "resumes": mock.resumes.keys().cloned().collect::<Vec<_>>(),
        "subscriptions": mock.subscriptions.keys().cloned().collect::<Vec<_>>(),
        "responses": mock.responses.values().map(Vec::len).sum::<usize>()
    }))
}

fn html_escape(value: &str) -> String {
    value
        .replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
        .replace('\'', "&#39;")
}

fn resume_label(id: &str) -> String {
    match id {
        "resume-strong" => "Сильное резюме (Java)".to_string(),
        "resume-weak" => "Слабое резюме (Python)".to_string(),
        "resume-empty" => "Пустое резюме".to_string(),
        value if value.starts_with("resume-custom-") => "Своё резюме".to_string(),
        value => value.to_string(),
    }
}

fn error_page(title: &str, message: &str) -> String {
    format!(
        "<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\">\
         <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\
         <title>{title}</title>{style}</head><body>{header}\
         <main><h1>{title}</h1><div class=\"error\">{message}</div>\
         <p><a class=\"back\" href=\"/\">← Все вакансии</a></p></main></body></html>",
        title = html_escape(title),
        message = html_escape(message),
        style = PAGE_STYLE,
        header = PAGE_HEADER
    )
}

const PAGE_HEADER: &str = "<header><span class=\"logo\">hh</span>\
    <span class=\"brand\">mock hh.ru</span>\
    <span class=\"env\">локальная заглушка</span></header>";

const PAGE_STYLE: &str = "<style>\
    *{box-sizing:border-box}\
    body{margin:0;background:#f3f4f6;color:#1f2937;font:15px/1.5 system-ui,-apple-system,'Segoe UI',sans-serif}\
    header{display:flex;align-items:center;gap:12px;padding:14px 28px;background:#d6001c;color:#fff}\
    .logo{display:grid;place-items:center;width:40px;height:40px;border-radius:12px;background:#fff;color:#d6001c;font-weight:800;font-size:18px}\
    .brand{font-weight:700;font-size:18px}\
    .env{margin-left:auto;font-size:12px;opacity:.8;border:1px solid rgba(255,255,255,.5);padding:4px 8px;border-radius:999px}\
    main{max-width:920px;margin:0 auto;padding:32px 20px 64px}\
    h1{font-size:28px;line-height:1.2;margin:0 0 8px}\
    .muted{color:#6b7280;font-size:13px}\
    .grid{display:grid;gap:12px;margin-top:20px}\
    .card{display:block;padding:16px;border:1px solid #e5e7eb;border-radius:14px;background:#fff;color:inherit;text-decoration:none;transition:.15s}\
    .card:hover{border-color:#d6001c;box-shadow:0 8px 24px rgba(214,0,28,.08)}\
    .card-head{display:flex;align-items:center;justify-content:space-between;gap:12px}\
    .badge{font-size:11px;text-transform:uppercase;letter-spacing:.04em;background:#fef2f2;color:#b91c1c;padding:3px 8px;border-radius:999px}\
    .back{display:inline-block;margin-bottom:18px;color:#6b7280;text-decoration:none;font-size:13px}\
    .back:hover{color:#111827}\
    .meta{margin:8px 0 20px;color:#6b7280;font-size:13px}\
    .description{white-space:pre-wrap;background:#fff;border:1px solid #e5e7eb;border-radius:14px;padding:20px;margin-bottom:20px}\
    .respond{display:flex;flex-wrap:wrap;gap:10px;align-items:end;background:#fff;border:1px solid #e5e7eb;border-radius:14px;padding:16px}\
    .respond label{width:100%;font-size:13px;font-weight:600}\
    .respond select{flex:1;min-width:260px;padding:10px 12px;border:1px solid #d1d5db;border-radius:10px;background:#fff;font:inherit}\
    .custom{display:flex;flex-direction:column;gap:6px;width:100%;margin-top:6px}\
    .custom textarea,.custom input[type=file]{width:100%;padding:10px 12px;border:1px solid #d1d5db;border-radius:10px;background:#fff;font:inherit}\
    .custom textarea{resize:vertical;min-height:120px}\
    .respond button{padding:10px 16px;border:0;border-radius:10px;background:#d6001c;color:#fff;font:inherit;font-weight:600;cursor:pointer}\
    .respond button:hover{background:#b80018}\
    .success{margin:0 0 18px;padding:12px 14px;border-radius:12px;background:#ecfdf5;color:#047857;border:1px solid #a7f3d0}\
    .error{margin:16px 0;padding:12px 14px;border-radius:12px;background:#fef2f2;color:#b91c1c;border:1px solid #fecaca}\
    .empty{padding:20px;border:1px dashed #d1d5db;border-radius:14px;background:#fff;color:#6b7280}\
    .responses{margin-top:20px;background:#fff;border:1px solid #e5e7eb;border-radius:14px;padding:16px}\
    .responses h2{font-size:16px;margin:0 0 10px}\
    .responses ul{margin:0;padding:0;list-style:none;display:grid;gap:10px}\
    .responses li{padding:10px 12px;border:1px solid #f3f4f6;border-radius:10px;background:#fafafa}\
    </style>";

fn persist(state: &AppState, mock: &MockState) {
    let Some(path) = &state.state_file else {
        return;
    };
    if let Some(parent) = path.parent() {
        let _ = std::fs::create_dir_all(parent);
    }
    match serde_json::to_string(mock) {
        Ok(content) => {
            if let Err(error) = std::fs::write(path, content) {
                warn!("mock_hh_state_persist_failed error={}", error);
            }
        }
        Err(error) => warn!("mock_hh_state_serialize_failed error={}", error),
    }
}

fn seed_state() -> MockState {
    let mut resumes = HashMap::new();
    resumes.insert("resume-strong".to_string(), strong_resume("resume-strong"));
    resumes.insert("resume-weak".to_string(), weak_resume("resume-weak"));
    resumes.insert("resume-empty".to_string(), empty_resume("resume-empty"));
    MockState {
        // Уникальность между перезапусками: иначе после рестарта mock-hh
        // выдаёт те же hh_vacancy_id, и они конфликтуют с привязками в БД.
        next_id: Utc::now().timestamp_millis() as u64,
        drafts: HashMap::new(),
        vacancies: HashMap::new(),
        resumes,
        subscriptions: HashMap::new(),
        responses: HashMap::new(),
    }
}

fn strong_resume(id: &str) -> Value {
    json!({
        "id": id,
        "title": "Java-разработчик",
        "first_name": "Иван",
        "last_name": "Петров",
        "middle_name": null,
        "contact": [
            {"kind": "email", "contact_value": "ivan.petrov@example.com"},
            {"kind": "phone", "contact_value": "+7 999 123-45-67"}
        ],
        "skills": "Пять лет разработки на Java, Spring Boot и PostgreSQL.",
        "skill_set": ["Java", "Spring Boot", "PostgreSQL", "Kafka", "Docker"],
        "experience": [
            {
                "company": "Синтетическая компания",
                "position": "Senior Java Developer",
                "start": "2019-01-01",
                "end": null,
                "description": "Проектировал сервисы на Spring Boot, PostgreSQL, Kafka; настраивал Docker."
            }
        ],
        "education": {
            "level": {"name": "Высшее"},
            "primary": [
                {"name": "Синтетический университет", "organization": "Факультет ВМК", "result": "Прикладная математика", "year": 2018}
            ]
        },
        "language": [{"name": "Английский", "level": {"name": "B2"}}]
    })
}

fn weak_resume(id: &str) -> Value {
    json!({
        "id": id,
        "title": "Начинающий разработчик",
        "first_name": "Анна",
        "last_name": "Сидорова",
        "contact": [
            {"kind": "email", "contact_value": "anna.sidorova@example.com"}
        ],
        "skills": "Базовое знакомство с Python.",
        "skill_set": ["Python"],
        "experience": [],
        "education": {"level": {"name": "Высшее"}, "primary": []}
    })
}

fn empty_resume(id: &str) -> Value {
    json!({
        "id": id,
        "title": "",
        "first_name": "",
        "last_name": "",
        "contact": [],
        "skills": "",
        "skill_set": [],
        "experience": [],
        "education": {}
    })
}
