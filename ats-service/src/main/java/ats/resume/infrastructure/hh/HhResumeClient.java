package ats.resume.infrastructure.hh;

import ats.vacancy.hh.HhApiException;

/** Клиент резюме hh.ru: только чтение, без сохранения сырого JSON. */
public interface HhResumeClient {

    HhResume fetch(String resumeId, String topicId) throws HhApiException;
}
