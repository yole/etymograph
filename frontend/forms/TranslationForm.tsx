import {addTranslation, editTranslation} from "@/api";
import EtymographForm from "@/components/EtymographForm";
import FormTextArea from "@/components/FormTextArea";
import FormRow from "@/components/FormRow";
import {useContext} from "react";
import {GraphContext} from "@/components/Contexts";

export default function TranslationForm(props) {
    const graph = useContext(GraphContext)

    return <EtymographForm
        create={(data) => addTranslation(graph, props.corpusTextId, data)}
        update={(data) => editTranslation(graph, props.updateId, data)}
        {...props}
    >
        <FormTextArea rows={10} cols={50} id="text"/>
        <table><tbody>
            <FormRow label="Source" id="source"/>
        </tbody></table>
    </EtymographForm>
}
