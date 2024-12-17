import {addCorpusText, updateCorpusText} from "@/api";
import FormRow from "@/components/FormRow";
import EtymographForm, {EtymographFormProps} from "@/components/EtymographForm";
import FormTextArea from "@/components/FormTextArea";
import {useContext} from "react";
import {GraphContext} from "@/components/Contexts";
import {CorpusTextParams, CorpusTextViewModel} from "@/models";

interface CorpusTextFormProps extends EtymographFormProps<CorpusTextParams, CorpusTextViewModel> {
    lang: string;
}

export default function CorpusTextForm(props: CorpusTextFormProps) {
    const graph = useContext(GraphContext)
    return <EtymographForm<CorpusTextParams, CorpusTextViewModel>
        create={(data) => addCorpusText(graph, props.lang, data)}
        update={(data) => updateCorpusText(graph, props.updateId, data)}
        {...props}
    >
        <table><tbody>
            <FormRow id="title" label="Title"/>
        </tbody></table>
        <FormTextArea rows={10} cols={50} id="text" inputAssist={true} inputAssistLang={props.lang}/>
        <table>
            <tbody>
            <FormRow id="source" label="Source" />
            </tbody>
        </table>
        <h3>Notes</h3>
        <FormTextArea rows={5} cols={50} id="notes"/>
    </EtymographForm>
}
