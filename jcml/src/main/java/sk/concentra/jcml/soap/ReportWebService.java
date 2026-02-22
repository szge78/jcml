package sk.concentra.jcml.soap;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;
import jakarta.jws.soap.SOAPBinding;
import sk.concentra.jcml.soap.dto.GetReportResponse;

/**
 * JAX-WS Service Endpoint Interface (SEI) for the legacy getReport SOAP operation.
 * Namespace and service name match the legacy Grails SOAP endpoint.
 */
@WebService(
        name            = "ReportService",
        targetNamespace = "http://util.gcml.concentra.sk/"
)
@SOAPBinding(parameterStyle = SOAPBinding.ParameterStyle.BARE)
public interface ReportWebService {

    @WebMethod
    @WebResult(name = "getReportResponse", targetNamespace = "http://util.gcml.concentra.sk/")
    GetReportResponse getReport(
            @WebParam(name = "dateFrom") String dateFrom,
            @WebParam(name = "dateTo")   String dateTo
    );
}