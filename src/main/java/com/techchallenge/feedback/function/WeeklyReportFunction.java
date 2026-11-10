package com.techchallenge.feedback.function;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import com.techchallenge.feedback.service.EmailService;
import com.techchallenge.feedback.service.ReportService;

import java.util.Objects;
import java.util.logging.Level;

/**
 * Aciona a geração e o envio do relatório semanal por timer.
 */
public class WeeklyReportFunction {

    // Segunda-feira às 11h UTC (08h em America/Sao_Paulo), com o host em UTC.
    static final String CRON_SEGUNDA_08H_BRASILIA = "0 0 11 * * MON";

    private final ReportService reportService;
    private final EmailService emailService;

    /**
     * Cria a função com os serviços de relatório e e-mail padrão.
     */
    public WeeklyReportFunction() {
        this(new ReportService(), new EmailService());
    }

    WeeklyReportFunction(ReportService reportService, EmailService emailService) {
        this.reportService = Objects.requireNonNull(reportService, "reportService e obrigatorio");
        this.emailService = Objects.requireNonNull(emailService, "emailService e obrigatorio");
    }

    /**
     * Gera e envia o relatório; propaga falhas de consulta ou envio.
     *
     * @param timerInfo metadados do timer fornecidos pelo runtime
     * @param context contexto de execução e logs
     */
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
    }
}
