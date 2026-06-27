import {addTranslation, editTranslation} from "@/api";
import EtymographForm, {EtymographFormProps} from "@/components/EtymographForm";
import FormTextArea from "@/components/FormTextArea";
import SourceInput from "@/components/SourceInput";
import {useContext} from "react";
import {GraphContext} from "@/components/Contexts";
import {TranslationParams, TranslationViewModel} from "@/models";

interface TranslationFormProps extends EtymographFormProps<TranslationParams, TranslationViewModel> {
    corpusTextId: number;
    anchorStartIndex?: number;
}

export default function TranslationForm(props: TranslationFormProps) {
    const graph = useContext(GraphContext)

    return <EtymographForm
        create={(data) => addTranslation(graph, props.corpusTextId, data, props.anchorStartIndex)}
        update={(data) => editTranslation(graph, props.updateId as number, data)}
        {...props}
    >
        <FormTextArea rows={10} cols={50} id="text" className="uiTextArea"/>
        <table><tbody>
            <SourceInput label="Source" id="source"/>
        </tbody></table>
    </EtymographForm>
}
