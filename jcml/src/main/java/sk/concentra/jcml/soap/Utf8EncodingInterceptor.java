package sk.concentra.jcml.soap;

import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

/** Ensures every SOAP response declares UTF-8 in both the HTTP header and XML preamble. */
class Utf8EncodingInterceptor extends AbstractPhaseInterceptor<Message> {

    Utf8EncodingInterceptor() {
        super(Phase.PREPARE_SEND);
    }

    @Override
    public void handleMessage(Message message) {
        message.put(Message.ENCODING, "UTF-8");
    }
}
