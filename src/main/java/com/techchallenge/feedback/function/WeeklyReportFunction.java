package com.techchallenge.feedback.function;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import com.techchallenge.feedback.service.EmailService;
import com.techchallenge.feedback.service.ReportService;

import java.util.Objects;
import java.util.logging.Level;

public class WeeklyReportFunction {

    /*
     * Formato NCRONTAB: {segundo} {minuto} {hora} {dia} {mês} {dia-da-semana}
     *
     * Funções em planos serverless interpretam o cron em UTC, não no horário local.
     * Brasília está em UTC-3 (sem horário de verão desde 2019), então 08:00 em Brasília = 11:00 UTC.
     * "0 0 11 * * MON" executa toda segunda-feira às 08:00 de Brasília.
     */
    static final String CRON_SEGUNDA_08H_BRASILIA = "0 0 11 * * MON";

    private final ReportService reportService;
    private final EmailService emailService;

    public WeeklyReportFunction() {
        this(new ReportService(), new EmailService());
    }

    WeeklyReportFunction(ReportService reportService, EmailService emailService) {
        this.reportService = Objects.requireNonNull(reportService, "reportService e obrigatorio");
        this.emailService = Objects.requireNonNull(emailService, "emailService e obrigatorio");
    }

    @FunctionName("WeeklyReportFunction")
    public void run(
            @TimerTrigger(name = "timerInfo", schedule = CRON_SEGUNDA_08H_BRASILIA) String timerInfo,
            ExecutionContext context) {
        try {
            ReportService.RelatorioSemanal relatorio = reportService.gerarRelatorioSemanal();
            emailService.enviar(relatorio.assunto(), relatorio.corpo());
            context.getLogger().info(() -> "Relatorio semanal enviado: total=" + relatorio.total());
        } catch (RuntimeException exception) {
            context.getLogger().log(Level.SEVERE, "Erro ao enviar relatorio semanal", exception);
            throw exception;
        }
    }}