package sk.concentra.jcml

import groovy.util.logging.Slf4j
import io.micronaut.runtime.EmbeddedApplication
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import sk.concentra.jcml.persistence.AttributeRepository
import sk.concentra.jcml.persistence.CampaignRepository
import sk.concentra.jcml.persistence.ConfigMessageLogRepository
import sk.concentra.jcml.persistence.AgentRepository
import sk.concentra.jcml.persistence.AgentTeamRepository
import sk.concentra.jcml.persistence.SkillGroupRepository
import sk.concentra.jcml.persistence.PrecisionQueueRepository
import sk.concentra.jcml.service.MessageService
import spock.lang.Ignore
import spock.lang.Specification
import jakarta.inject.Inject

import java.time.LocalDateTime

@Slf4j
@MicronautTest
class JcmlSpec extends Specification {

    @Inject
    EmbeddedApplication<?> application

    @Inject
    ConfigMessageLogRepository configMessageLogRepository

    @Inject
    AgentRepository agentRepository

    @Inject
    AgentTeamRepository agentTeamRepository

    @Inject
    SkillGroupRepository skillGroupRepository

    @Inject
    PrecisionQueueRepository precisionQueueRepository

    @Inject
    AttributeRepository attributeRepository

    @Inject
    CampaignRepository campaignRepository

    @Inject
    MessageService messageService

    void 'test it works'() {
        expect:
        application.running
    }

    @Ignore("This usually works")
    void 'getConfigMessageLogForTheLast24Hours'() {
        var now = LocalDateTime.now()
        var before24Hours = now.minusHours(24)
        var entries = configMessageLogRepository.findAllByDateTimeBetweenOrderByRecoveryKeyAsc(before24Hours, now)
        var entriesSize = entries.size()
        log.info("entriesSize: {}", entriesSize)
        expect:
        entriesSize > 0
    } // void 'getConfigMessageLogForTheLast24Hours'() {

    @Ignore("This usually works")
    void 'getDeserializedFirstMessage'() {
        var deserializedMessage = messageService.getDeserializedMessageById(11354428035033d)
        log.info("deserializedMessage: {}", deserializedMessage)
        deserializedMessage.ifPresent {dm-> {
            dm.forEach { k, v ->
                log.info("k: {}, v: {}", k, v)
            }
        }}
        expect:
        deserializedMessage.isPresent()

    } // void 'getDeserializedFirstMessage'() {

    @Ignore("This usually works")
    void 'getDeserializedMessagesByRecoveryKeyRange_UpdateAgent'() {
        given:
        Double start = 11359439530375d  // 11354428035229d
        Double end =   11359439530379d  //11359439530379d   //11354428035231d //11354428035234d
        
        when:
        var messages = messageService.getDeserializedMessagesByRecoveryKeyRange(start, end)
        log.info("messages size: {}", messages.size())

        messages.each {m ->
            log.info("m: {}", m)
            m.each {k, v ->
                log.info(" k: {}, v: {}", k, v)
            }
        }
        
        then:
        messages != null
    }

    @Ignore("This usually works")
    void 'getDeserializedMessagesByRecoveryKeyRange_UpdateSmart_License_Info'() {
        given:
        Double start = 11359439530495d
        Double end =   11359439530498d

        when:
        var messages = messageService.getDeserializedMessagesByRecoveryKeyRange(start, end)
        log.info("messages size: {}", messages.size())

        messages.each {m ->
            log.info("m: {}", m)
            m.each {k, v ->
                log.info(" k: {}, v: {}", k, v)
            }
        }

        then:
        messages != null
    }

    @Ignore("This usually works")
    void 'getDeserializedMessagesByRecoveryKeyRange_UpdateSmart_License_Entitlements'() {
        given:
        Double start = 11359439530619d
        Double end =   11359439530622d

        when:
        var messages = messageService.getDeserializedMessagesByRecoveryKeyRange(start, end)
        log.info("messages size: {}", messages.size())

        messages.each {m ->
            log.info("m: {}", m)
            m.each {k, v ->
                log.info(" k: {}, v: {}", k, v)
            }
        }

        then:
        messages != null
    }

    @Ignore("This usually works")
    void 'getDeserializedMessagesByRecoveryKeyRange_Add__Skill_Group_Member'() {
        given:
        Double start = 11359439531104d
        Double end =   11359439531109d

        when:
        var messages = messageService.getDeserializedMessagesByRecoveryKeyRange(start, end)
        log.info("messages size: {}", messages.size())

        messages.each {m ->
            log.info("m: {}", m)
            m.each {k, v ->
                log.info(" k: {}, v: {}", k, v)
            }
        }

        then:
        messages != null
    }

    @Ignore("This usually works")
    void 'getDeserializedMessagesByRecoveryKeyRange_Update_Agent_Attribute'() {
        given:
        Double start = 11359439531129d
        Double end =   11359439531135d

        when:
        var messages = messageService.getDeserializedMessagesByRecoveryKeyRange(start, end)
        log.info("messages size: {}", messages.size())

        messages.each {m ->
            log.info("m: {}", m)
            m.each {k, v ->
                log.info(" k: {}, v: {}", k, v)
            }
        }

        then:
        messages != null
    }

    @Ignore("This usually works")
    void 'getDeserializedMessagesByRecoveryKeyRange_DELETE__Skill_Group_Member'() {
        given:
        Double start = 11359439531143d
        Double end =   11359439531149d

        when:
        var messages = messageService.getDeserializedMessagesByRecoveryKeyRange(start, end)
        log.info("messages size: {}", messages.size())

        messages.each {m ->
            log.info("m: {}", m)
            m.each {k, v ->
                log.info(" k: {}, v: {}", k, v)
            }
        }

        then:
        messages != null
    }

    @Ignore("This usually works")
    void 'getDeserializedMessagesByRecoveryKeyRange_UPDATE__IMPORT_RULE'() {
        given:
        Double start = 11361424468750d
        Double end =   11361424468756d // 11361424468756d

        when:
        var messages = messageService.getDeserializedMessagesByRecoveryKeyRange(start, end)
        log.info("messages size: {}", messages.size())

        messages.each {m ->
            log.info("m: {}", m)
            m.each {k, v ->
                log.info(" k: {}, v: {}", k, v)
            }
        }

        then:
        messages != null
    }

    @Ignore("This usually works")
    void 'getDeserializedMessagesByRecoveryKeyRange_UPDATE__PRECISION_QUEUE'() {
        given:
        Double start = 11361424472117d
        Double end =   11361424472124d

        when:
        var messages = messageService.getDeserializedMessagesByRecoveryKeyRangeParallel(start, end)
        log.info("[D] messages size: {}", messages.size())

        messages.each {objectNode ->
            log.info("[D] objectNode: {}", objectNode)
            def onSize = objectNode.size()
            objectNode.fieldNames().eachWithIndex { String fieldName, int i ->
                def field = objectNode.get(fieldName)
                log.info("[D][$i/$onSize] $fieldName: $field")
            }

//            m.each {k, v ->
//                log.info(" k: {}, v: {}", k, v)
//            }
        }

        then:
        messages != null
    }


    @Ignore
    void 'getDeserializedMessagesByRecoveryKeyRange_and_send_through_pipeline_UPDATE__PRECISION_QUEUE'() {
        given:
        Double start = 11361424472117d
        Double end =   11361424472124d

        when:
        var messages = messageService.getDeserializedMessagesByRecoveryKeyRangeParallel(start, end)
        log.info("[D] messages size: {}", messages.size())
        var processMessagesFlux = messageService.processMessages(messages)
        var messagesIterable = processMessagesFlux.toIterable()

        messagesIterable.each {objectNode ->
            log.info("[D] objectNode: {}", objectNode)
            def onSize = objectNode.size()
            objectNode.fieldNames().eachWithIndex { String fieldName, int i ->
                def field = objectNode.get(fieldName)
                log.info("[D][$i/$onSize] $fieldName: $field")
            } // eachwithindex
        } // each

        then:
        messages != null
    }

    @Ignore
    void 'getDeserializedMessagesByRecoveryKeyRange_and_send_through_pipeline_UPDATE__AGENT_ATTRIBUTE'() {
        given:
        Double start = 11278440701725d
        Double end =   11278440701732d

        when:
        var messages = messageService.getDeserializedMessagesByRecoveryKeyRangeParallel(start, end)
        log.info("[D] messages size: {}", messages.size())
        var processMessagesFlux = messageService.processMessages(messages)
        var messagesIterable = processMessagesFlux.toIterable()

        messagesIterable.each {objectNode ->
            log.info("[D] objectNode: {}", objectNode)
            def onSize = objectNode.size()
            objectNode.fieldNames().eachWithIndex { String fieldName, int i ->
                def field = objectNode.get(fieldName)
                log.info("[D][$i/$onSize] $fieldName: $field")
            } // eachwithindex
        } // each

        then:
        messages != null
    }

    @Ignore
    void 'getDeserializedMessagesByRecoveryKeyRange_and_send_through_pipeline_ADD__AGENT_TEAM'() {
        given:
        Double start = 11284433664006d
        Double end =   11284433664009d

        when:
        var messages = messageService.getDeserializedMessagesByRecoveryKeyRangeParallel(start, end)
        log.info("[D] messages size: {}", messages.size())
        var processMessagesFlux = messageService.processMessages(messages)
        var messagesIterable = processMessagesFlux.toIterable()

        messagesIterable.each {objectNode ->
            log.info("[D] objectNode: {}", objectNode)
            def onSize = objectNode.size()
            objectNode.fieldNames().eachWithIndex { String fieldName, int i ->
                def field = objectNode.get(fieldName)
                log.info("[D][$i/$onSize] $fieldName: $field")
            } // eachwithindex
        } // each

        then:
        messages != null
    }

    @Ignore
    void 'getDeserializedMessagesByRecoveryKeyRange_and_send_through_pipeline_DELETE__AGENT_ATTRIBUTE'() {
        given:
        Double start = 11361424861213d
        Double end =   11361424861219d

        when:
        var messages = messageService.getDeserializedMessagesByRecoveryKeyRangeParallel(start, end)
        log.info("[D] messages size: {}", messages.size())
        var processMessagesFlux = messageService.processMessages(messages)
        var messagesIterable = processMessagesFlux.toIterable()

        messagesIterable.each {objectNode ->
            log.info("[D] objectNode: {}", objectNode)
            def onSize = objectNode.size()
            objectNode.fieldNames().eachWithIndex { String fieldName, int i ->
                def field = objectNode.get(fieldName)
                log.info("[D][$i/$onSize] $fieldName: $field")
            } // eachwithindex
        } // each

        then:
        messages != null
    }

    @Ignore
    void 'getDeserializedMessagesByRecoveryKeyRange_and_send_through_pipeline_add_and_delete_agent_team_member'() {
        given:
        Double start = 11368429495246d
        Double end =   11368429495250d

        when:
        var messages = messageService.getDeserializedMessagesByRecoveryKeyRangeParallel(start, end)
        log.info("[D] messages size: {}", messages.size())
        var processMessagesFlux = messageService.processMessages(messages)
        var messagesIterable = processMessagesFlux.toIterable()

        messagesIterable.each {objectNode ->
            log.info("[D] objectNode: {}", objectNode)
            def onSize = objectNode.size()
            objectNode.fieldNames().eachWithIndex { String fieldName, int i ->
                def field = objectNode.get(fieldName)
                log.info("[D][$i/$onSize] $fieldName: $field")
            } // eachwithindex
        } // each

        then:
        messages != null
    }

    @Ignore
    void 'getDeserializedMessagesByRecoveryKeyRange_and_send_through_pipeline_add_agent_and_add_person'() {
        given:
        Double start = 11326430995183d
        Double end =   11326430995188d

        when:
//        var messages = messageService.getDeserializedMessagesByRecoveryKeyRangeParallel(start, end)
        var messages = messageService.getDeserializedMessagesByRecoveryKeyRange(start, end)
        log.info("[D] messages size: {}", messages.size())
        var processMessagesFlux = messageService.processMessages(messages)
        var messagesIterable = processMessagesFlux.toIterable()

        messagesIterable.each {objectNode ->
            log.info("[D] objectNode: {}", objectNode)
            def onSize = objectNode.size()
            objectNode.fieldNames().eachWithIndex { String fieldName, int i ->
                def field = objectNode.get(fieldName)
                log.info("[D][$i/$onSize] $fieldName: $field")
            } // eachwithindex
        } // each

        then:
        messages != null
    }

    void 'getDeserializedMessagesByRecoveryKeyRange_and_send_through_pipeline_2026-02-18'() {
        given:
//        Double start = 11368429427282d
//        Double end =   11368429554910d

//        DELETE__SKILL_GROUP_MEMBER:
        Double start = 11368429427805d
        Double end = 11368429427809d

        // PQ:
//        Double start = 11361424472117d
//        Double end =   11361424472124d

        when:
        var messages = messageService.getDeserializedMessagesByRecoveryKeyRangeParallel(start, end)
//        var messages = messageService.getDeserializedMessagesByRecoveryKeyRange(start, end)
        log.info("[D] messages size: {}", messages.size())
        var processMessagesFlux = messageService.processMessages(messages)
        var messagesIterable = messageService.processMessages(messages)
        var size = messagesIterable.size()
        log.info("messagesIterable.size(): {}", size)

        messagesIterable.each {objectNode ->
            def _messageType = objectNode.get("_messageType")
            def _dbMetadata = objectNode.get("_dbMetadata")
            def recoveryKey = _dbMetadata.get("recoveryKey")
            def _full_description = objectNode.get("_full_description")
            def humanReadableTimestamp = objectNode.get("humanReadableTimestamp")
            def _userName = objectNode.get("_userName")
            log.info("$recoveryKey $humanReadableTimestamp $_userName $_messageType _full_description: $_full_description")
            log.info("[D] objectNode: {}", objectNode)
            def onSize = objectNode.size()
            objectNode.fieldNames().eachWithIndex { String fieldName, int i ->
                def field = objectNode.get(fieldName)
                log.info("[D][$i/$onSize] $fieldName: $field")
            } // eachwithindex
        } // each

        then:
        messages != null
    }


    @Ignore
    void 'getAgentBySkillTargetId'() {
        given:
        Integer skillTargetId = 5653

        when:
        var agent = agentRepository.getBySkillTargetId(skillTargetId)
        log.info("agent: {}", agent)

        then:
        agent != null
        agent.enterpriseName == 'Baday_Michaela'
    }

    @Ignore
    void 'getAgentTeamByAgentTeamId'() {
        given:
        Integer agentTeamId = 5022

        when:
        var agentTeam = agentTeamRepository.getByAgentTeamId(agentTeamId)
        log.info("agentTeam: {}", agentTeam)

        then:
        agentTeam != null
        agentTeam.enterpriseName == 'OUT_Zlin_1'
    }

    @Ignore
    void 'getSkillGroupBySkillTargetId'() {
        given:
        Integer skillTargetId = 5008

        when:
        var skillGroup = skillGroupRepository.getBySkillTargetId(skillTargetId)
        log.info("skillGroup: {}", skillGroup)

        then:
        skillGroup != null
        skillGroup.enterpriseName == 'CMB_Benefity'
    }

    @Ignore
    void 'getPrecisionQueueByPrecisionQueueId'() {
        given:
        Integer precisionQueueId = 5013

        when:
        var precisionQueue = precisionQueueRepository.getByPrecisionQueueId(precisionQueueId)
        log.info("precisionQueue: {}", precisionQueue)

        then:
        precisionQueue != null
        precisionQueue.enterpriseName == 'Distribuce_transfer_pd'
    }

    @Ignore
    void 'getAttributeByAttributeId'() {
        given:
        Integer attributeId = 5017

        when:
        var attribute = attributeRepository.getByAttributeId(attributeId)
        log.info("attribute: {}", attribute)

        then:
        attribute != null
        attribute.enterpriseName == 'E2E_metodik'
    }


    void 'getCampaignByCampaignId'() {
        given:
        Integer campaignId = 5010

        when:
        var campaign = campaignRepository.getByCampaignId(campaignId)
        log.info("campaign: {}", campaign)

        then:
        campaign != null
        campaign.campaignName == 'CMB_Retence'
    }
}
