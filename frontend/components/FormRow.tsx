import InputAssist from "@/components/InputAssist";
import {useEtymographFormContext} from "@/components/EtymographForm";
import {Input} from "@mantine/core";

export interface FormFieldProps {
    id: string;
    label: string;
}

interface FormRowProps extends FormFieldProps{
    readOnly?: boolean;
    size?: number;
    inputAssist?: boolean;
    inputAssistLanguage?: string;
    inputAssistLanguageProp?: string;
    children?: React.ReactNode;
    handleBlur?: (data: any) => void;
}

export default function FormRow(props: FormRowProps) {
    const form = useEtymographFormContext()

    return <tr onBlur={() => {
        if (props.handleBlur !== undefined) props.handleBlur(form.getValues())
    }}>
        <td><Input.Label htmlFor={props.id}>{props.label}:</Input.Label></td>
        <td>
            <input id={props.id} readOnly={props.readOnly} size={props.size} type="text" autoComplete="off"
                   data-1p-ignore={true}
                   className="formRow" {...form.getInputProps(props.id)}/>
            {props.inputAssist &&
                <InputAssist id={props.id}
                             language={props.inputAssistLanguage}
                             languageProp={props.inputAssistLanguageProp} inline={true}
                />
            }
            {props.children}
        </td>
    </tr>
}
