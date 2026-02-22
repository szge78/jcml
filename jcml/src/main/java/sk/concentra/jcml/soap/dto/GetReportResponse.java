package sk.concentra.jcml.soap.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.xml.bind.annotation.*;
import java.util.List;

/**
 * Response wrapper for the getReport operation.
 *
 * <p>Note: deliberately uses {@code @XmlType} only — no {@code @XmlRootElement}.
 * CXF generates its own wrapper element from the {@code @WebResult} annotation on
 * the SEI, and having {@code @XmlRootElement} here as well causes JAXB to see two
 * classes with the same XML type name, throwing {@code IllegalAnnotationsException}.</p>
 */
@Serdeable
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "getReportResponse", namespace = "http://util.gcml.concentra.sk/")
@XmlType(name = "getReportResponse", namespace = "http://util.gcml.concentra.sk/")
public class GetReportResponse {

    @XmlElement(name = "reportRow")
    private List<ReportRow> reportRows;

    public GetReportResponse() {}

    public GetReportResponse(List<ReportRow> reportRows) {
        this.reportRows = reportRows;
    }

    public List<ReportRow> getReportRows() { return reportRows; }
    public void setReportRows(List<ReportRow> reportRows) { this.reportRows = reportRows; }
}